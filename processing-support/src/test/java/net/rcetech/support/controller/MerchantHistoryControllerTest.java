package net.rcetech.support.controller;

import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.config.ProcessingConfigurationProperties;
import net.rcetech.support.service.MerchantHistoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MerchantHistoryController.class)
@Import({ MetaSecurityConfig.class })
@EnableConfigurationProperties(ProcessingConfigurationProperties.class)
class MerchantHistoryControllerTest {

    @MockitoBean
    private MerchantHistoryService merchantHistoryService;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Метод должен вызвать сервис истории для роли ADMIN")
    void get_shouldCallServiceForAdmin() throws Exception {
        UUID adminId = UUID.randomUUID();
        mockMvc.perform(get("/api/private/merchant-history")
                        .with(user(adminId.toString()).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());
        verify(merchantHistoryService).getHistory(any(), any(Pageable.class));
    }

    @Test
    @DisplayName("Метод должен вызвать сервис истории для роли OPERATOR")
    void get_shouldCallServiceForOperator() throws Exception {
        UUID operatorId = UUID.randomUUID();
        mockMvc.perform(get("/api/private/merchant-history")
                        .with(user(operatorId.toString()).roles("OPERATOR"))
                        .with(csrf()))
                .andExpect(status().isOk());
        verify(merchantHistoryService).getHistory(any(), any(Pageable.class));
    }

    @ParameterizedTest
    @ValueSource(strings = { "CLIENT", "USER", "GUEST" })
    @DisplayName("Метод должен вернуть статус 403, если запрос выполняется не ADMIN и не OPERATOR")
    void get_shouldReturn403ForForbiddenRoles(String role) throws Exception {
        UUID userId = UUID.randomUUID();
        mockMvc.perform(get("/api/private/merchant-history")
                        .with(user(userId.toString()).roles(role))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

}
