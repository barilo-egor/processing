package net.rcetech.billing.controller;

import net.rcetech.domain.service.billing.WithdrawalRequestService;
import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.config.ProcessingConfigurationProperties;
import net.rcetech.meta.user.Role;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClientWithdrawalRequestController.class)
@Import({MetaSecurityConfig.class})
@EnableConfigurationProperties(ProcessingConfigurationProperties.class)
class ClientWithdrawalRequestControllerTest {

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WithdrawalRequestService withdrawalRequestService;

    @Test
    @DisplayName("Метод должен вернуть 400, если отсутствует параметр amount.")
    void create_shouldReturn400IfNoAmount() throws Exception {
        mockMvc.perform(post("/api/v1/withdrawal_request")
                .with(csrf())
                .with(user("6aec494b-62be-4eee-be50-41c6e1164052").roles(Role.CLIENT.name())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("Required parameter 'amount' is not present")));
    }

    @Test
    @DisplayName("Метод должен вернуть 400, если отсутствует параметр address.")
    void create_shouldReturn400IfNoAddress() throws Exception {
        mockMvc.perform(post("/api/v1/withdrawal_request")
                        .queryParam("amount", "100")
                        .with(csrf())
                        .with(user("6aec494b-62be-4eee-be50-41c6e1164052").roles(Role.CLIENT.name())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("Required parameter 'address' is not present")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ADMIN", "OPERATOR"
    })
    @DisplayName("Метод должен вернуть 403, если запрос выполняет не клиент.")
    void create_shouldReturn403IfNotClient(String role) throws Exception {
        mockMvc.perform(post("/api/v1/withdrawal_request")
                .queryParam("amount", "100")
                .queryParam("address", "TX9zFakeAddressTRC20usdtNotReal99x")
                .with(csrf())
                .with(user("6aec494b-62be-4eee-be50-41c6e1164052").roles(role)))
        .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({
            "25167,TX9zFakeAddressTRC20usdtNotReal99x",
            "500000,TF7y6fakeTronAddressUSDT88888xxxx1"
    })
    @DisplayName("Метод должен передать параметры в метод сервиса.")
    void create_shouldPassParametersToService(Integer amount, String address) throws Exception {
        UUID clientId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/withdrawal_request")
                        .queryParam("amount", amount.toString())
                        .queryParam("address", address)
                        .with(csrf())
                        .with(user(clientId.toString()).roles(Role.CLIENT.name())))
                .andExpect(status().isCreated());
        verify(withdrawalRequestService).create(clientId, amount, address);
    }


    @ParameterizedTest
    @ValueSource(strings = {
            "ADMIN", "OPERATOR"
    })
    @DisplayName("Метод должен вернуть 403, если запрос выполняет не клиент.")
    void cancel_shouldReturn403IfNotClient(String role) throws Exception {
        mockMvc.perform(patch("/api/v1/withdrawal_request")
                        .with(csrf())
                        .with(user("6aec494b-62be-4eee-be50-41c6e1164052").roles(role)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({
            "df5df58e-9fb8-4c56-9a77-e938c16501c5,fe0b457d-e4d4-4b78-b155-bf3475bb3518",
            "0ed874cc-30a1-4645-ac2e-1a1814e1017b,0e845a87-ebef-44c0-b702-ae996b26f537"
    })
    @DisplayName("Метод должен передать параметры в метод сервиса.")
    void cancel_shouldPassParametersToService(UUID clientId, UUID id) throws Exception {
        mockMvc.perform(patch("/api/v1/withdrawal_request/" + id.toString())
                        .with(csrf())
                        .with(user(clientId.toString()).roles("CLIENT")))
                .andExpect(status().isOk());
        verify(withdrawalRequestService).cancel(clientId, id);
    }
}