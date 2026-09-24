package net.rcetech.billing.controller;

import net.rcetech.domain.repository.orders.MerchantCallbackSpecifications;
import net.rcetech.domain.service.orders.MerchantCallbackService;
import net.rcetech.meta.billing.dto.MerchantCallbackFilter;
import net.rcetech.meta.billing.dto.MerchantCallbackResponse;
import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.config.ProcessingConfigurationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MerchantCallbackController.class)
@Import({MetaSecurityConfig.class})
@EnableConfigurationProperties(ProcessingConfigurationProperties.class)
class MerchantCallbackControllerTest {

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MerchantCallbackService merchantCallbackService;

    @ParameterizedTest
    @ValueSource(strings = {"CLIENT", "TRADER"})
    @DisplayName("Метод должен вернуть 403, если роль пользователя не админ и не оператор.")
    void get_shouldReturn403IfNotAdminOrOperator(String role) throws Exception {
        mockMvc.perform(get("/api/private/merchant-callback")
                .with(csrf())
                .with(user("38c3db77-c4d7-4b21-818e-aa9731b26fe8").roles(role))
        ).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({
            "1df5da43-b74a-4046-8381-c25fc993516b,ALFA_TEAM,faf4aa7b-2435-449d-a282-8fa7e7b49028,1790262068808,1790262268808",
            "435945a3-48da-43b4-8fb2-5ea7d34f63f5,EVO_PAY,598724,1790262058800,1790262090000"
    })
    @DisplayName("Метод должен передать параметры в фильтр.")
    void get_shouldPassFilterParameters(UUID orderId,
                                        Merchant merchant,
                                        String merchantOrderId,
                                        long createdAtFrom,
                                        long createdAtTo) throws Exception {
        when(merchantCallbackService.findAll(any(), any(), any())).thenReturn(new PageImpl<>(List.of(mock(MerchantCallbackResponse.class))));
        try (MockedStatic<MerchantCallbackSpecifications> mockedSpecifications
                     = Mockito.mockStatic(MerchantCallbackSpecifications.class)) {
            mockMvc.perform(get("/api/private/merchant-callback")
                    .with(csrf())
                    .with(user("38c3db77-c4d7-4b21-818e-aa9731b26fe8").roles("ADMIN"))
                    .queryParam("orderId", orderId.toString())
                    .queryParam("merchant", merchant.name())
                    .queryParam("merchantOrderId", merchantOrderId)
                    .queryParam("createdAtFrom", String.valueOf(createdAtFrom))
                    .queryParam("createdAtTo", String.valueOf(createdAtTo))
            ).andExpect(status().isOk());
            ArgumentCaptor<MerchantCallbackFilter> captor = ArgumentCaptor.forClass(MerchantCallbackFilter.class);
            mockedSpecifications.verify(() -> MerchantCallbackSpecifications.matches(captor.capture()));
            MerchantCallbackFilter filter = captor.getValue();
            assertAll(
                    () -> assertEquals(orderId, filter.orderId()),
                    () -> assertEquals(merchant, filter.merchant()),
                    () -> assertEquals(merchantOrderId, filter.merchantOrderId()),
                    () -> assertEquals(Instant.ofEpochMilli(createdAtTo), filter.createdAtTo()),
                    () -> assertEquals(Instant.ofEpochMilli(createdAtFrom), filter.createdAtFrom())
            );
        }
    }

    @CsvSource({
            "1,1790262068808,1df5da43-b74a-4046-8381-c25fc993516b,ALFA_TEAM,bb7ec0c6-9f7b-497f-8c18-06ecba259f49,PAID,Оплачено",
            "69643546,1790262068808,435945a3-48da-43b4-8fb2-5ea7d34f63f5,EVO_PAY,83561,TIMEOUT,Истек"
    })
    @ParameterizedTest
    void get_shouldReturnJsonOfMerchantCallbackResponse(long id, long createdAt, UUID orderId, Merchant merchant,
                                                        String merchantOrderId, String status, String statusDescription
    ) throws Exception {
        MerchantCallbackResponse response = new MerchantCallbackResponse(
                id, Instant.ofEpochMilli(createdAt), orderId, merchant, merchantOrderId, status, statusDescription
        );
        when(merchantCallbackService.findAll(any(), any(), any())).thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/private/merchant-callback")
                .with(csrf())
                .with(user("38c3db77-c4d7-4b21-818e-aa9731b26fe8").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(String.valueOf(id)))
                .andExpect(jsonPath("$.content[0].createdAt").value(String.valueOf(createdAt)))
                .andExpect(jsonPath("$.content[0].orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.content[0].merchant").value(merchant.name()))
                .andExpect(jsonPath("$.content[0].merchantOrderId").value(merchantOrderId))
                .andExpect(jsonPath("$.content[0].status").value(status))
                .andExpect(jsonPath("$.content[0].statusDescription").value(statusDescription))
                .andExpect(jsonPath("$.page").exists());
    }

}