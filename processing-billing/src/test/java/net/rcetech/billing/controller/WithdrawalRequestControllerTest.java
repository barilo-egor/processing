package net.rcetech.billing.controller;

import net.rcetech.domain.repository.billing.WithdrawalRequestSpecifications;
import net.rcetech.domain.service.billing.WithdrawalRequestService;
import net.rcetech.meta.billing.WithdrawalRequestStatus;
import net.rcetech.meta.billing.dto.WithdrawalRequestFilter;
import net.rcetech.meta.billing.dto.WithdrawalRequestResponse;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WithdrawalRequestController.class)
@Import({MetaSecurityConfig.class})
@EnableConfigurationProperties(ProcessingConfigurationProperties.class)
class WithdrawalRequestControllerTest {

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WithdrawalRequestService withdrawalRequestService;

    @ParameterizedTest
    @ValueSource(strings = {
            "CLIENT",
            "TRADER"
    })
    @DisplayName("Метод должен вернуть 403, если роль пользователя не ADMIN и OPERATOR.")
    void get_shouldReturn403IfNotAdminOrOperator(String role) throws Exception {
        mockMvc.perform(get("/api/private/withdrawal-request")
                .with(csrf())
                .with(user("673dd1e7-b780-4a8f-871c-01b61bbbc1c1").roles(role))
        ).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "673dd1e7-b780-4a8f-871c-01b61bbbc1c1,1c63edce-0f57-440a-bef0-2da2baddde10,NEW,1790120780869,1790130780869," +
                    "TX9zFakeAddressTRC20usdtNotReal99x",
            "1ad7b862-880a-4851-91f2-5ccdc367068a,lolik,APPROVED,1790120630800,1790120640800,TX9zFakeAddressTRC20usdtNotReal99x"
    })
    @DisplayName("Метод должен передать параметры запроса в метод сервиса.")
    void get_shouldPassFilterParameters(UUID id, String client, WithdrawalRequestStatus status, long createdAtFrom,
                                        long createdAtTo, String address) throws Exception {
        WithdrawalRequestResponse withdrawalRequestResponse = mock(WithdrawalRequestResponse.class);
        when(withdrawalRequestService.findAll(any(), any(), any())).thenReturn(new PageImpl<>(List.of(withdrawalRequestResponse)));
        UUID clientId = UUID.randomUUID();
        try (MockedStatic<WithdrawalRequestSpecifications> mockedSpecifications = Mockito.mockStatic(WithdrawalRequestSpecifications.class)) {
            mockMvc.perform(get("/api/private/withdrawal-request")
                    .with(csrf())
                    .with(user(clientId.toString()).roles("ADMIN"))
                    .queryParam("id", id.toString())
                    .queryParam("client", client)
                    .queryParam("status", status.toString())
                    .queryParam("createdAtFrom", String.valueOf(createdAtFrom))
                    .queryParam("createdAtTo", String.valueOf(createdAtTo))
                    .queryParam("address", address)
            ).andExpect(status().isOk());
            ArgumentCaptor<UUID> clientIdCaptor = ArgumentCaptor.forClass(UUID.class);
            ArgumentCaptor<WithdrawalRequestFilter> filterCaptor = ArgumentCaptor.forClass(WithdrawalRequestFilter.class);

            mockedSpecifications.verify(() -> WithdrawalRequestSpecifications.matches(
                    clientIdCaptor.capture(),
                    filterCaptor.capture()
            ));

            assertNull(clientIdCaptor.getValue());

            WithdrawalRequestFilter filter = filterCaptor.getValue();
            assertNotNull(filter, "Фильтр не должен быть null");
            assertEquals(id, filter.id());
            assertEquals(client, filter.client());
            assertEquals(status, filter.status());
            assertEquals(Instant.ofEpochMilli(createdAtFrom), filter.createdAtFrom());
            assertEquals(Instant.ofEpochMilli(createdAtTo), filter.createdAtTo());
            assertEquals(address, filter.address());
        }
    }

    @CsvSource({
            "cb07e9aa-f6be-4f17-b574-6fd160855544,lolik,33093a34-3fe3-4a8d-a054-cc8ea0984b76,NEW,1790175372283," +
                    "10000,10.0,9000,100,90,TX9zFakeAddressTRC20usdtNotReal99x",
            "6a0c0065-187f-44d4-976f-dea6a2b21039,bolik,187d52c2-f74e-4fd7-8b35-f15c6c424428,APPROVED,1790175970000," +
                    "20000,20.0,16000,50,320,TF7y6fakeTronAddressUSDT88888xxxx1 "
    })
    @ParameterizedTest
    @DisplayName("Метод должен вернуть JSON представление заявки.")
    void get_shouldReturnRequestJson(UUID clientId, String clientUsername, UUID id, WithdrawalRequestStatus status,
                                     Long createdAt, Integer grossSourceAmount, BigDecimal commissionPercent,
                                     Integer netSourceAmount, BigDecimal rate, Integer targetAmount,
                                     String address) throws Exception {
        WithdrawalRequestResponse withdrawalRequestResponse = new WithdrawalRequestResponse(
                clientId, clientUsername, id, status, Instant.ofEpochMilli(createdAt), grossSourceAmount,
                commissionPercent, netSourceAmount, rate, targetAmount, address
        );
        when(withdrawalRequestService.findAll(any(), any(), any())).thenReturn(new PageImpl<>(List.of(withdrawalRequestResponse)));

        mockMvc.perform(get("/api/private/withdrawal-request")
                .with(csrf())
                .with(user("46c7f3fd-a6af-40d3-b843-f0052790518a").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].clientId").value(clientId.toString()))
                .andExpect(jsonPath("$.content[0].clientUsername").value(clientUsername))
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.content[0].status").value(status.name()))
                .andExpect(jsonPath("$.content[0].createdAt").value(createdAt))
                .andExpect(jsonPath("$.content[0].grossSourceAmount").value(grossSourceAmount))
                .andExpect(jsonPath("$.content[0].commissionPercent").value(commissionPercent.doubleValue()))
                .andExpect(jsonPath("$.content[0].netSourceAmount").value(netSourceAmount))
                .andExpect(jsonPath("$.content[0].rate").value(rate.doubleValue()))
                .andExpect(jsonPath("$.content[0].targetAmount").value(targetAmount))
                .andExpect(jsonPath("$.content[0].address").value(address));
    }
}