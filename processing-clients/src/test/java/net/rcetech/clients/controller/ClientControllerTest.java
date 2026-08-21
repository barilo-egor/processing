package net.rcetech.clients.controller;

import net.rcetech.clients.config.ClientsSecurityConfig;
import net.rcetech.clients.service.KeycloakEventService;
import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.config.ProcessingConfigurationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClientController.class)
@Import({ClientsSecurityConfig.class, MetaSecurityConfig.class})
@EnableConfigurationProperties(ProcessingConfigurationProperties.class)
class ClientControllerTest {

    @MockitoBean
    private KeycloakEventService keycloakEventService;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @DisplayName("Сериализация объекта должна пройти без ошибок.")
    @ValueSource(strings = {"login_event.json", "login_error_event.json", "refresh_token_event.json",
            "login_event_with_unknown_field.json"})
    @WithMockUser(roles = {"WEBHOOK_CLIENT"})
    void event_shouldSerializeJson(String fileName) throws Exception {
        String json = new String(new ClassPathResource("/controller/keycloak/" + fileName).getInputStream().readAllBytes());
        mockMvc.perform(
                post("/client/event/")
                        .header("Content-Type", "application/json")
                        .content(json))
                .andExpect(status().isCreated());
    }
}