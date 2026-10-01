package net.rcetech.support.controller;

import net.rcetech.domain.service.support.SupportUserService;
import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.config.ProcessingConfigurationProperties;
import net.rcetech.meta.support.dto.SupportUserResponse;
import net.rcetech.meta.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SupportUserController.class)
@Import({MetaSecurityConfig.class})
@EnableConfigurationProperties(ProcessingConfigurationProperties.class)
class SupportUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupportUserService supportUserService;

    @MockitoBean
    private AuthenticationSuccessHandler successHandler;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @ParameterizedTest
    @CsvSource({
            "c2cbb845-7313-4106-a1a3-e9e4b328bbd9,test1,1790874344130",
            "fe4a4710-9c2e-4f04-b274-06802fbf2012,smokilolik1,1790872344000"
    })
    @DisplayName("Метод должен вернуть JSON представление пользователя поддержки.")
    void get_shouldReturnSupportUserJSON(UUID id, String username, Long millis) throws Exception {
        SupportUserResponse supportUserResponse = new SupportUserResponse(
                id, username, Instant.ofEpochMilli(millis)
        );
        when(supportUserService.findById(id, SupportUserResponse.class)).thenReturn(Optional.of(supportUserResponse));

        mockMvc.perform(get("/api/private/support-user")
                        .with(csrf())
                        .with(user(id.toString()).roles(Role.ADMIN.name())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.registeredAt").value(millis));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLIENT", "TRADER"})
    @DisplayName("Метод должен вернуть 403 для не пользователей поддержки.")
    void get_shouldReturn403IfNotSupportUser(String role) throws Exception {
        mockMvc.perform(get("/api/private/support-user")
                .with(csrf())
                .with(user("c2cbb845-7313-4106-a1a3-e9e4b328bbd9").roles(role)))
                .andExpect(status().isForbidden());
    }
}