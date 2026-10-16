package net.rcetech.support.controller;

import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.user.Role;
import net.rcetech.support.service.MerchantConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MerchantConfigController.class)
@Import({ MetaSecurityConfig.class })
class MerchantConfigControllerTest {

    @MockitoBean
    private MerchantConfigService merchantConfigService;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private MockMvc mockMvc;

    @RepeatedTest(value = 2)
    @DisplayName("Метод должен вызвать метод сервиса.")
    void get_ShouldReturnConfigs() throws Exception {
        UUID merchantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        mockMvc.perform(get("/api/support/merchant-config/" + merchantId)
                .with(user(adminId.toString()).roles(Role.ADMIN.name()))
                .with(csrf()))
                .andExpect(status().isOk());
        verify(merchantConfigService).findAll(merchantId);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"CLIENT", "OPERATOR"})
    @DisplayName("Метод должен вернуть статус 403, если запрос выполняется не клиентом или оператором.")
    void get_shouldReturn403IfNotAdmin(Role role) throws Exception {
        UUID merchantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        mockMvc.perform(get("/api/support/merchant-config/" + merchantId)
                        .with(user(userId.toString()).roles(role.name()))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}