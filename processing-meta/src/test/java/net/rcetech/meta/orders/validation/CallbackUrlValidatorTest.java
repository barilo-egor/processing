package net.rcetech.meta.orders.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallbackUrlValidatorTest {

    record Target(@CallbackUrl String callbackUrl) { }

    private static ValidatorFactory validatorFactory;

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/callback",
            "https://api.shop.ru:443/orders/callback?token=abc",
            "HTTPS://Example.COM/cb",
            "https://example.com./callback",
            "https://xn--80ak6aa92e.xn--p1ai/callback",
            "https://my-site.online"
    })
    @NullSource
    @DisplayName("Валидатор должен пропустить https-адрес с публичным доменным именем или null.")
    void isValid_shouldPassAllowedUrlOrNull(String url) {
        assertTrue(validator.validate(new Target(url)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            " ",
            "qwerty",
            "example.com/callback",
            "http://example.com/callback",
            "ftp://example.com/callback",
            "https:example.com/callback",
            "https:///callback",
            "https://example.com:8443/callback",
            "https://example.com:80/callback",
            "https://user:pass@example.com/callback",
            "https://example.com@evil.com/callback",
            "https://example.com/callback#part",
            "https://example.com/call back",
            " https://example.com/callback",
            "https://localhost/callback",
            "https://LOCALHOST/callback",
            "https://api.localhost/callback",
            "https://printer.local/callback",
            "https://metadata.google.internal/callback",
            "https://keycloak/callback",
            "https://mysql",
            "https://127.0.0.1/callback",
            "https://10.0.0.5/callback",
            "https://8.8.8.8/callback",
            "https://169.254.169.254/latest/meta-data",
            "https://127.1/callback",
            "https://2130706433/callback",
            "https://[::1]/callback",
            "https://[2001:db8::1]/callback",
            "https://exa_mple.com/callback",
            "https://.com/callback"
    })
    @DisplayName("Валидатор должен вернуть нарушение по полю callbackUrl, если адрес не соответствует требованиям.")
    void isValid_shouldRejectNotAllowedUrl(String url) {
        Set<ConstraintViolation<Target>> violations = validator.validate(new Target(url));

        assertEquals(1, violations.size());
        ConstraintViolation<Target> violation = violations.iterator().next();
        assertEquals("callbackUrl", violation.getPropertyPath().toString());
        assertEquals("должно быть адресом с протоколом https и портом 443 не длиннее 255 символов, "
                + "с доменным именем публичного сервера, без логина и пароля", violation.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {256, 300})
    @DisplayName("Валидатор должен вернуть нарушение, если адрес длиннее 255 символов.")
    void isValid_shouldRejectTooLongUrl(int length) {
        String prefix = "https://example.com/";
        String url = prefix + "a".repeat(length - prefix.length());

        assertEquals(1, validator.validate(new Target(url)).size());
    }

    @ParameterizedTest
    @ValueSource(ints = {254, 255})
    @DisplayName("Валидатор должен пропустить адрес длиной до 255 символов включительно.")
    void isValid_shouldPassUrlUpToMaxLength(int length) {
        String prefix = "https://example.com/";
        String url = prefix + "a".repeat(length - prefix.length());

        assertTrue(validator.validate(new Target(url)).isEmpty());
    }
}
