package net.rcetech.clients.cloudflare;

import net.rcetech.domain.service.clients.IpAddressService;
import net.rcetech.meta.clients.IpAddressesChangedEvent;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.incidents.CloudflareIpSyncErrorEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CloudflareIpListSynchronizerTest {

    @Mock
    private CloudflareIpListClient cloudflareIpListClient;

    @Mock
    private IpAddressService ipAddressService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CloudflareIpListSynchronizer synchronizer;

    @BeforeEach
    void setUp() {
        lenient().when(ipAddressService.toCanonical(anyString())).thenAnswer(invocation -> {
            try {
                return Optional.of(InetAddress.getByName(invocation.<String>getArgument(0)).getHostAddress());
            } catch (UnknownHostException e) {
                return Optional.empty();
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.0.0.1", "2001:db8:0:0:0:0:0:1"})
    @DisplayName("Используемый адрес, которого нет в Cloudflare, должен добавляться.")
    void sync_shouldAddUsedAddressMissingInCloudflare(String address) {
        when(ipAddressService.isUsed(address)).thenReturn(true);
        when(cloudflareIpListClient.findAll()).thenReturn(List.of(new CloudflareIpListClient.Item("a1", "10.9.9.9")));

        synchronizer.ipAddressesChanged(new IpAddressesChangedEvent(List.of(address)));

        verify(cloudflareIpListClient).add(address);
        verify(cloudflareIpListClient, never()).delete(any());
        verifyNoInteractions(eventPublisher);
    }

    @ParameterizedTest
    @CsvSource({
            "10.0.0.1, 10.0.0.1",
            "10.0.0.1, 10.0.0.1/32",
            "2001:db8:0:0:0:0:0:1, 2001:db8::1",
            "2001:db8:0:0:0:0:0:1, 2001:db8::1/128"
    })
    @DisplayName("Используемый адрес, который уже есть в Cloudflare в любой записи, не должен добавляться повторно.")
    void sync_shouldNotAddAddressPresentInCloudflare(String address, String cloudflareIp) {
        when(ipAddressService.isUsed(address)).thenReturn(true);
        when(cloudflareIpListClient.findAll()).thenReturn(List.of(new CloudflareIpListClient.Item("a1", cloudflareIp)));

        synchronizer.ipAddressesChanged(new IpAddressesChangedEvent(List.of(address)));

        verify(cloudflareIpListClient, never()).add(any());
        verify(cloudflareIpListClient, never()).delete(any());
    }

    @ParameterizedTest
    @CsvSource({
            "10.0.0.1, 10.0.0.1, item-1",
            "2001:db8:0:0:0:0:0:1, 2001:db8::1, item-2"
    })
    @DisplayName("Неиспользуемый адрес, который есть в Cloudflare, должен удаляться по идентификатору записи.")
    void sync_shouldDeleteUnusedAddressPresentInCloudflare(String address, String cloudflareIp, String itemId) {
        when(ipAddressService.isUsed(address)).thenReturn(false);
        when(cloudflareIpListClient.findAll()).thenReturn(List.of(
                new CloudflareIpListClient.Item("other", "10.0.0.10"),
                new CloudflareIpListClient.Item(itemId, cloudflareIp)));

        synchronizer.ipAddressesChanged(new IpAddressesChangedEvent(List.of(address)));

        verify(cloudflareIpListClient).delete(itemId);
        verify(cloudflareIpListClient, never()).add(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.0.0.1", "2001:db8:0:0:0:0:0:1"})
    @DisplayName("Неиспользуемый адрес, которого нет в Cloudflare, не должен ни добавляться, ни удаляться.")
    void sync_shouldDoNothingForUnusedAddressMissingInCloudflare(String address) {
        when(ipAddressService.isUsed(address)).thenReturn(false);
        when(cloudflareIpListClient.findAll()).thenReturn(List.of(new CloudflareIpListClient.Item("a1", "10.0.0.10")));

        synchronizer.ipAddressesChanged(new IpAddressesChangedEvent(List.of(address)));

        verify(cloudflareIpListClient, never()).add(any());
        verify(cloudflareIpListClient, never()).delete(any());
    }

    @ParameterizedTest
    @CsvSource({
            "10.0.0.1, true, добавить, Cloudflare недоступен",
            "10.0.0.2, false, удалить, Операция завершилась ошибкой"
    })
    @DisplayName("При ошибке Cloudflare должен публиковаться инцидент с адресом, действием и текстом ошибки.")
    void sync_shouldPublishIncidentOnError(String address, boolean used, String action, String error) {
        when(ipAddressService.isUsed(address)).thenReturn(used);
        when(cloudflareIpListClient.findAll()).thenReturn(used
                ? List.of()
                : List.of(new CloudflareIpListClient.Item("a1", address)));
        if (used) {
            doThrow(new BaseException(error)).when(cloudflareIpListClient).add(address);
        } else {
            doThrow(new BaseException(error)).when(cloudflareIpListClient).delete("a1");
        }

        synchronizer.ipAddressesChanged(new IpAddressesChangedEvent(List.of(address)));

        ArgumentCaptor<CloudflareIpSyncErrorEvent> captor = ArgumentCaptor.forClass(CloudflareIpSyncErrorEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        CloudflareIpSyncErrorEvent event = captor.getValue();
        assertAll(
                () -> assertEquals(address, event.address()),
                () -> assertEquals(action, event.action()),
                () -> assertEquals(error, event.error()),
                () -> assertTrue(event.errorTimestamp() > 0)
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 4})
    @DisplayName("Ошибка по одному адресу не должна мешать синхронизации остальных.")
    void sync_shouldContinueAfterError(int count) {
        List<String> addresses = IntStream.range(0, count).mapToObj(i -> "10.0.0." + i).toList();
        addresses.forEach(address -> when(ipAddressService.isUsed(address)).thenReturn(true));
        when(cloudflareIpListClient.findAll()).thenThrow(new BaseException("Ошибка")).thenReturn(List.of());

        synchronizer.ipAddressesChanged(new IpAddressesChangedEvent(addresses));

        verify(eventPublisher, times(1)).publishEvent(any(CloudflareIpSyncErrorEvent.class));
        addresses.stream().skip(1).forEach(address -> verify(cloudflareIpListClient).add(address));
    }
}
