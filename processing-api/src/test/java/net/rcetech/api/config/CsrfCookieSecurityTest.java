package net.rcetech.api.config;

import jakarta.servlet.http.Cookie;
import net.rcetech.domain.test.SharedMySQLContainer;
import net.rcetech.meta.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CsrfCookieSecurityTest {

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        SharedMySQLContainer.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("Кука XSRF-TOKEN выдаётся с флагом Secure и доступна из JS.")
    void csrfCookieShouldBeSecureAndReadableFromJs(Role role) throws Exception {
        Cookie cookie = mockMvc.perform(get("/logout")
                        .with(user(UUID.randomUUID().toString()).roles(role.name())))
                .andReturn()
                .getResponse()
                .getCookie("XSRF-TOKEN");

        assertThat(cookie).isNotNull();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isFalse();
    }
}
