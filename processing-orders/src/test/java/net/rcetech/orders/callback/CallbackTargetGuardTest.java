package net.rcetech.orders.callback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

class CallbackTargetGuardTest {

    @ParameterizedTest
    @CsvSource({
            "https://example.com/callback,example.com,93.184.216.34",
            "https://api.shop.ru:443/cb,api.shop.ru,8.8.8.8;2001:4860:4860::8888"
    })
    @DisplayName("Адрес с хостом, разрешающимся только в публичные IP-адреса, должен проходить проверку.")
    void check_shouldPassIfAllAddressesPublic(String url, String host, String ips) throws UnknownHostException {
        CallbackTargetGuard guard = spy(new CallbackTargetGuard());
        doReturn(addresses(ips)).when(guard).resolve(host);

        assertDoesNotThrow(() -> guard.check(url));
    }

    @ParameterizedTest
    @CsvSource({
            "https://example.com/callback,example.com,127.0.0.1,127.0.0.1",
            "https://rebind.shop.ru/cb,rebind.shop.ru,8.8.8.8;10.0.0.5,10.0.0.5",
            "https://meta.evil.net/cb,meta.evil.net,169.254.169.254,169.254.169.254",
            "https://v6.evil.net/cb,v6.evil.net,2001:4860:4860::8888;fd00::1,fd00:0:0:0:0:0:0:1"
    })
    @DisplayName("Адрес, хост которого разрешается хотя бы в один внутренний IP-адрес, должен отклоняться.")
    void check_shouldRejectIfAnyAddressInternal(String url, String host, String ips, String internal)
            throws UnknownHostException {
        CallbackTargetGuard guard = spy(new CallbackTargetGuard());
        doReturn(addresses(ips)).when(guard).resolve(host);

        ForbiddenCallbackUrlException exception = assertThrows(ForbiddenCallbackUrlException.class,
                () -> guard.check(url));
        assertEquals("хост " + host + " указывает на внутренний адрес " + internal, exception.getMessage());
    }

    @ParameterizedTest
    @CsvSource({
            "https://unknown.example/callback,unknown.example",
            "https://down.shop.ru/cb,down.shop.ru"
    })
    @DisplayName("Ошибка разрешения хоста должна пробрасываться как UnknownHostException.")
    void check_shouldThrowUnknownHostIfNotResolved(String url, String host) throws UnknownHostException {
        CallbackTargetGuard guard = spy(new CallbackTargetGuard());
        doThrow(new UnknownHostException(host)).when(guard).resolve(host);

        assertThrows(UnknownHostException.class, () -> guard.check(url));
    }

    private InetAddress[] addresses(String ips) {
        return Arrays.stream(ips.split(";"))
                .map(ip -> {
                    try {
                        return InetAddress.getByName(ip);
                    } catch (UnknownHostException e) {
                        throw new IllegalArgumentException(e);
                    }
                })
                .toArray(InetAddress[]::new);
    }
}
