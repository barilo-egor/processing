package net.rcetech.api.controller;

import net.rcetech.api.merchantdetails.ApiMerchantDetailsGrpcService;
import net.rcetech.api.merchantdetails.MerchantCallbackDTO;
import net.rcetech.meta.Profiles;
import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.config.ProcessingConfigurationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tgb.cryptoexchange.commons.enums.Merchant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MerchantTestCallbackController.class)
@Import({MetaSecurityConfig.class})
@EnableConfigurationProperties(ProcessingConfigurationProperties.class)
class MerchantTestCallbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiMerchantDetailsGrpcService apiMerchantDetailsGrpcService;

    @Nested
    @ActiveProfiles(Profiles.TEST_MERCHANT_CALLBACK)
    class WithProfile {

        @ParameterizedTest
        @CsvSource({
                "ALFA_TEAM,SUCCESS,Оплачен,b7a4d9b0-6f2a-435f-ae45-5b71c9f379d0",
                "LOTRIEN,SUCCESS,Успешно,1530795"
        })
        @DisplayName("Метод должен сериализовать тело запроса и передать объект в сервис.")
        void send_shouldPassBodyToService(Merchant merchant, String status, String statusDescription,
                                          String merchantOrderId) throws Exception {
            mockMvc.perform(post("/api/public/merchant-callback")
                            .header("Content-Type", "application/json")
                            .content(String.format("{\"merchant\":\"%s\"," +
                                    "\"status\":\"%s\"," +
                                    "\"statusDescription\":\"%s\"," +
                                    "\"merchantOrderId\":\"%s\"}",
                                    merchant.name(), status, statusDescription, merchantOrderId)))
                    .andExpect(status().isOk());
            ArgumentCaptor<MerchantCallbackDTO> callbackCaptor = ArgumentCaptor.forClass(MerchantCallbackDTO.class);
            verify(apiMerchantDetailsGrpcService).getCallback(callbackCaptor.capture());
            MerchantCallbackDTO actual = callbackCaptor.getValue();
            assertAll(
                    () -> assertEquals(merchant, actual.getMerchant()),
                    () -> assertEquals(status, actual.getStatus()),
                    () -> assertEquals(statusDescription, actual.getStatusDescription()),
                    () -> assertEquals(merchantOrderId, actual.getMerchantOrderId())
            );
        }
    }

    @Nested
    class WithoutProfile {

        @Test
        @DisplayName("Приложение должно вернуть 404, если профиль для тестовых КБ не включен.")
        void send_shouldReturn404IfTestProfileNotActive() throws Exception {
            mockMvc.perform(post("/api/public/merchant-callback")
                    .header("Content-Type", "application/json")
                            .content("{\"merchant\":\"LOTRIEN\"," +
                                    "\"status\":\"SUCCESS\"," +
                                    "\"statusDescription\":\"Успешно\"," +
                                    "\"merchantOrderId\":\"qwerty12345\"}"))
                    .andExpect(status().isNotFound());
        }
    }
}