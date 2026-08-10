package net.rcetech.support.service.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseCookie;
import org.springframework.test.util.ReflectionTestUtils;
import net.rcetech.support.service.CookieService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CookieServiceTest {

    private final Long refreshExpiration = 86400L;

    @Mock
    private Environment env;

    @InjectMocks
    private CookieService cookieService;

    private void initServiceWithTtl() {
        ReflectionTestUtils.setField(cookieService, "refreshExpiration", refreshExpiration);
    }

    @Test
    @DisplayName("Создание куки на продакшн окружении (флаги Secure и SameSite=Strict)")
    void should_createStrictSecureCookie_when_profileIsProd() {
        initServiceWithTtl();
        String token = "prod_refresh_token_123";

        when(env.getActiveProfiles()).thenReturn(new String[] { "prod", "cloud" });

        ResponseCookie cookie = cookieService.createRefreshTokenCookie(token);

        assertNotNull(cookie);
        assertEquals("refreshToken", cookie.getName());
        assertEquals(token, cookie.getValue());
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.isSecure());
        assertEquals("/", cookie.getPath());
        assertEquals(refreshExpiration, cookie.getMaxAge().getSeconds());
        assertEquals("Strict", cookie.getSameSite());
    }

    @Test
    @DisplayName("Создание куки на dev окружении (флаги не Secure и SameSite=Lax)")
    void should_createLaxNonSecureCookie_when_profileIsDev() {
        initServiceWithTtl();
        String token = "dev_refresh_token_123";

        when(env.getActiveProfiles()).thenReturn(new String[] { "dev-web-app" });

        ResponseCookie cookie = cookieService.createRefreshTokenCookie(token);

        assertNotNull(cookie);
        assertEquals("refreshToken", cookie.getName());
        assertEquals(token, cookie.getValue());
        assertTrue(cookie.isHttpOnly());
        assertFalse(cookie.isSecure());
        assertEquals("/", cookie.getPath());
        assertEquals(refreshExpiration, cookie.getMaxAge().getSeconds());
        assertEquals("Lax", cookie.getSameSite());
    }

}
