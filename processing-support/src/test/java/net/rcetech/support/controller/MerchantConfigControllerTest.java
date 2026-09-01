package net.rcetech.support.controller;

import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.config.ProcessingConfigurationProperties;
import net.rcetech.support.service.MerchantConfigService;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
@EnableConfigurationProperties(ProcessingConfigurationProperties.class)
class MerchantConfigControllerTest {

    @MockitoBean
    private MerchantConfigService merchantConfigService;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private MockMvc mockMvc;

    @RepeatedTest(value = 2)
    void get_ShouldReturnConfigs() throws Exception {
        UUID merchantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        mockMvc.perform(get("/api/private/merchant-config/" + merchantId)
                .with(user(adminId.toString()).roles("ADMIN"))
                .with(csrf()))
                .andExpect(status().isOk());
        verify(merchantConfigService).findAll(merchantId);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "CLIENT", "OPERATOR"
    })
    void get_shouldReturn403IfNotAdmin(String role) throws Exception {
        UUID merchantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        mockMvc.perform(get("/api/private/merchant-config/" + merchantId)
                        .with(user(userId.toString()).roles(role))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}