package net.rcetech.meta.orders.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallbackUrlPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "93.184.216.34", "100.63.255.255", "100.128.0.1", "2001:4860:4860::8888",
            "2a00:1450:4010::64"})
    @DisplayName("Публичный IP-адрес должен считаться допустимым.")
    void isPublicAddress_shouldReturnTrueForPublicAddress(String ip) throws UnknownHostException {
        assertTrue(CallbackUrlPolicy.isPublicAddress(InetAddress.getByName(ip)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1", "127.10.0.1",
            "0.0.0.0", "0.1.2.3",
            "10.0.0.1", "10.255.255.255",
            "172.16.0.1", "172.31.255.255",
            "192.168.0.1", "192.168.100.5",
            "169.254.169.254", "169.254.0.1",
            "100.64.0.1", "100.127.255.255",
            "224.0.0.1", "239.255.255.250",
            "::", "::1",
            "fe80::1", "fec0::1",
            "fc00::1", "fd12:3456::1",
            "ff02::1",
            "::ffff:127.0.0.1", "::ffff:10.0.0.1"
    })
    @DisplayName("Локальный, приватный, служебный или multicast IP-адрес должен считаться недопустимым.")
    void isPublicAddress_shouldReturnFalseForInternalAddress(String ip) throws UnknownHostException {
        assertFalse(CallbackUrlPolicy.isPublicAddress(InetAddress.getByName(ip)));
    }
}
