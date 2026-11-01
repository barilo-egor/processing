package net.rcetech.support.controller;

import net.rcetech.meta.GlobalControllerAdvice;
import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.user.Role;
import net.rcetech.support.service.MerchantConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MerchantConfigController.class)
@Import({ MetaSecurityConfig.class, GlobalControllerAdvice.class })
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

    @ParameterizedTest
    @CsvSource({
            "1,maxAmount,0",
            "25,maxAmount,-500",
            "3,minAmount,0",
            "40,minAmount,-1",
            "7,merchantOrder,0",
            "12,merchantOrder,-3"
    })
    @DisplayName("Метод должен вернуть 400, если сумма или очерёдность не больше нуля.")
    void update_shouldReturn400IfNotPositive(Long id, String field, int value) throws Exception {
        mockMvc.perform(patch("/api/support/merchant-config/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"" + field + "\": " + value + "}")
                        .with(user(UUID.randomUUID().toString()).roles(Role.ADMIN.name()))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value("Поле «" + field + "» должно быть больше нуля."));
        verify(merchantConfigService, never()).update(anyLong(), any());
    }

    @ParameterizedTest
    @CsvSource({
            "1,maxAmount,1",
            "25,minAmount,500",
            "7,merchantOrder,1",
            "12,maxAmount,100000"
    })
    @DisplayName("Метод должен передать изменение в сервис, если значение больше нуля.")
    void update_shouldCallServiceIfPositive(Long id, String field, int value) throws Exception {
        mockMvc.perform(patch("/api/support/merchant-config/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"" + field + "\": " + value + "}")
                        .with(user(UUID.randomUUID().toString()).roles(Role.ADMIN.name()))
                        .with(csrf()))
                .andExpect(status().isOk());
        verify(merchantConfigService).update(eq(id), any());
    }
}
