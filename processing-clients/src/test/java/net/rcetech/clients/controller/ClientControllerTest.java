package net.rcetech.clients.controller;

import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.meta.clients.dto.ClientUpdateRequest;
import net.rcetech.meta.clients.projection.ClientProjection;
import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.config.ProcessingConfigurationProperties;
import net.rcetech.meta.orders.RequestMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClientController.class)
@Import({MetaSecurityConfig.class})
@EnableConfigurationProperties(ProcessingConfigurationProperties.class)
class ClientControllerTest {

    @MockitoBean
    private ClientRegistrationRepository  clientRegistrationRepository;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClientService clientService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @CsvSource({
            "89b4e05b-c129-4ea3-a1d0-088d347b9cd2,smokilolik,1790181102840,http://example.com/callback," +
                    "900,15.5,255695",
            "a2c47cba-7b49-4d9b-961c-31220f3636e4,test1,1790181102840,http://google.net/callback," +
                    "800,10.0,387"
    })
    @DisplayName("Метод должен вернуть JSON клиента.")
    void get_shouldReturnClientJson(UUID id, String username, Long registeredAt, String callbackUrl,
                                    Integer orderTimeoutSeconds, BigDecimal commissionPercent, Integer balance) throws Exception {
        ClientProjection clientResponseDTO = new ClientProjection() {
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public String getUsername() {
                return username;
            }

            @Override
            public Instant getRegisteredAt() {
                return Instant.ofEpochMilli(registeredAt);
            }

            @Override
            public String getCallbackUrl() {
                return callbackUrl;
            }

            @Override
            public Integer getOrderTimeoutSeconds() {
                return orderTimeoutSeconds;
            }

            @Override
            public BigDecimal getCommissionPercent() {
                return commissionPercent;
            }

            @Override
            public Integer getBalance() {
                return balance;
            }

            @Override
            public Set<RequestMethod> getMethods() {
                return Set.of(RequestMethod.CARD, RequestMethod.SBP);
            }
        };

        when(clientService.findById(id, ClientProjection.class)).thenReturn(Optional.of(clientResponseDTO));

        mockMvc.perform(get("/api/v1/client")
                        .with(csrf())
                        .with(user(id.toString()).roles("CLIENT"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.registeredAt").value(registeredAt))
                .andExpect(jsonPath("$.callbackUrl").value(callbackUrl))
                .andExpect(jsonPath("$.orderTimeoutSeconds").value(orderTimeoutSeconds))
                .andExpect(jsonPath("$.commissionPercent").value(commissionPercent.doubleValue()))
                .andExpect(jsonPath("$.balance").value(balance));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"callbackUrl\":\"https://example.com/callback\"}",
            "{\"callbackUrl\":\"https://google.com/merchant-details/callback\"}"
    })
    @DisplayName("Параметры запроса должны быть переданы в метод сервиса.")
    void update_shouldPassParameters(String json) throws Exception {
        ClientUpdateRequest expected = objectMapper.readValue(json, ClientUpdateRequest.class);
        UUID clientId = UUID.randomUUID();
        mockMvc.perform(patch("/api/v1/client")
                .with(csrf())
                .with(user(clientId.toString()).roles("CLIENT"))
                        .header("Content-type", "application/json")
                .content(json))
                .andExpect(status().isOk());
        ArgumentCaptor<ClientUpdateRequest> captor = ArgumentCaptor.forClass(ClientUpdateRequest.class);
        verify(clientService).update(eq(clientId), captor.capture());
        assertEquals(expected.callbackUrl(), captor.getValue().callbackUrl());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ADMIN", "OPERATOR"
    })
    @DisplayName("Метод должен вернуть 403, если запрос выполняет не клиент.")
    void update_shouldReturn403IfNotClient(String role) throws Exception {
        mockMvc.perform(patch("/api/v1/client")
                        .with(csrf())
                        .with(user(UUID.randomUUID().toString()).roles(role)))
                .andExpect(status().isForbidden());
    }
}