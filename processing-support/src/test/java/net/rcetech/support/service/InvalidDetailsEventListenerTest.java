package net.rcetech.support.service;

import net.rcetech.meta.incidents.InvalidMerchantDetailsEvent;
import net.rcetech.meta.orders.InvalidDetailsReceivedEvent;
import net.rcetech.meta.orders.RequestMethod;
import net.rcetech.meta.support.dto.MerchantConfigResponseDTO;
import net.rcetech.meta.support.dto.MerchantConfigUpdateDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvalidDetailsEventListenerTest {

    private static final UUID ORDER_ID = UUID.fromString("6f1c2b9e-3d4a-4b8e-9a2f-1e7d5c3b2a10");

    private static final UUID CLIENT_ID = UUID.fromString("13afb6d4-6e61-4555-a0ae-072c6407ddb5");

    @Mock
    private MerchantConfigService merchantConfigService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private InvalidDetailsEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new InvalidDetailsEventListener(merchantConfigService, eventPublisher);
    }

    private InvalidDetailsReceivedEvent event(Merchant merchant) {
        return new InvalidDetailsReceivedEvent(ORDER_ID, CLIENT_ID, merchant, "58213", RequestMethod.CARD,
                "реквизиты переданы ссылкой", 1_791_100_000_000L);
    }

    private InvalidMerchantDetailsEvent incident(Merchant merchant, String result) {
        return new InvalidMerchantDetailsEvent(ORDER_ID, CLIENT_ID, merchant, "реквизиты переданы ссылкой", result,
                1_791_100_000_000L);
    }

    @Test
    @DisplayName("Слушатель должен отключить мерчанта клиенту и создать инцидент.")
    void invalidDetailsReceived_shouldDisableMerchantAndCreateIncident() {
        when(merchantConfigService.findAll(CLIENT_ID)).thenReturn(List.of(
                new MerchantConfigResponseDTO(1L, true, Merchant.ONLY_PAYS, 5000, 1, 1),
                new MerchantConfigResponseDTO(2L, true, Merchant.PAYSCROW_TRANSGRAN, 5000, 1, 2)));

        listener.invalidDetailsReceived(event(Merchant.PAYSCROW_TRANSGRAN));

        verify(merchantConfigService).update(2L, new MerchantConfigUpdateDTO(false, null, null, null));
        verify(eventPublisher).publishEvent(incident(Merchant.PAYSCROW_TRANSGRAN, "мерчант отключён"));
    }

    @Test
    @DisplayName("Если настройки мерчанта у клиента нет, слушатель должен создать инцидент без отключения.")
    void invalidDetailsReceived_shouldCreateIncidentIfConfigNotFound() {
        when(merchantConfigService.findAll(CLIENT_ID)).thenReturn(List.of(
                new MerchantConfigResponseDTO(1L, true, Merchant.ONLY_PAYS, 5000, 1, 1)));

        listener.invalidDetailsReceived(event(Merchant.PAYSCROW_TRANSGRAN));

        verify(merchantConfigService, never()).update(anyLong(), any());
        verify(eventPublisher).publishEvent(incident(Merchant.PAYSCROW_TRANSGRAN,
                "настройка мерчанта у клиента не найдена"));
    }

    @Test
    @DisplayName("Если отключить мерчанта не удалось, слушатель должен создать инцидент с причиной.")
    void invalidDetailsReceived_shouldCreateIncidentIfDisableFailed() {
        when(merchantConfigService.findAll(CLIENT_ID)).thenThrow(new IllegalStateException("gRPC service error"));

        listener.invalidDetailsReceived(event(Merchant.PAYSCROW_TRANSGRAN));

        verify(eventPublisher).publishEvent(incident(Merchant.PAYSCROW_TRANSGRAN,
                "отключить мерчанта не удалось (gRPC service error)"));
    }

    @Test
    @DisplayName("Если мерчант не указан, слушатель должен создать инцидент, не обращаясь к настройкам.")
    void invalidDetailsReceived_shouldCreateIncidentWithoutMerchant() {
        listener.invalidDetailsReceived(event(null));

        verifyNoInteractions(merchantConfigService);
        verify(eventPublisher).publishEvent(incident(null, "мерчант не указан, отключение не выполнялось"));
    }
}
