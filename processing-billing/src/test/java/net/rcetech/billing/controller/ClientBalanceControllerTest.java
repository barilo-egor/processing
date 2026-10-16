package net.rcetech.billing.controller;

import net.rcetech.billing.service.ClientBalanceService;
import net.rcetech.meta.clients.projection.SupportClientProjection;
import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.exception.BadRequestException;
import net.rcetech.meta.orders.RequestMethod;
import net.rcetech.meta.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClientBalanceController.class)
@Import({MetaSecurityConfig.class})
class ClientBalanceControllerTest {

    private static final String RECALCULATION_PATH = "/api/support/client/{id}/balance/recalculation";

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private ClientBalanceService clientBalanceService;

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {
            "a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7",
            "20484949-7339-4e19-86c9-d1e2c492f75f"
    })
    @DisplayName("Клиент не должен иметь доступа к пересчёту баланса.")
    void recalculate_shouldNotHaveAccessForClient(UUID id) throws Exception {
        mockMvc.perform(post(RECALCULATION_PATH, id)
                        .with(csrf())
                        .with(user(id.toString()).roles(Role.CLIENT.name())))
                .andExpect(status().isForbidden());
        verifyNoInteractions(clientBalanceService);
    }

    @ParameterizedTest
    @CsvSource({
            "ADMIN,a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7,70664",
            "OPERATOR,20484949-7339-4e19-86c9-d1e2c492f75f,0",
            "ADMIN,5d0c1c2e-0b6a-4f43-9c1d-3f1f7c2a9e10,-2105"
    })
    @DisplayName("Пользователь поддержки должен пересчитать баланс клиента и получить клиента после пересчёта.")
    void recalculate_shouldReturnClientAfterRecalculation(String role, UUID id, Integer balance) throws Exception {
        when(clientBalanceService.recalculate(id, SupportClientProjection.class)).thenReturn(client(id, balance));

        mockMvc.perform(post(RECALCULATION_PATH, id)
                        .with(csrf())
                        .with(user("cf1f0a52-3c1b-4a0e-9d7e-6a2b8f4e1c33").roles(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.username").value("user_" + id))
                .andExpect(jsonPath("$.balance").value(balance));
        verify(clientBalanceService).recalculate(id, SupportClientProjection.class);
    }

    @ParameterizedTest
    @CsvSource({
            "ADMIN,a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7",
            "OPERATOR,20484949-7339-4e19-86c9-d1e2c492f75f"
    })
    @DisplayName("Если клиент не найден, должен вернуться статус 400.")
    void recalculate_shouldReturnBadRequestIfClientNotFound(String role, UUID id) throws Exception {
        when(clientBalanceService.recalculate(id, SupportClientProjection.class))
                .thenThrow(new BadRequestException("Client with id " + id + " not found"));

        mockMvc.perform(post(RECALCULATION_PATH, id)
                        .with(csrf())
                        .with(user("cf1f0a52-3c1b-4a0e-9d7e-6a2b8f4e1c33").roles(role)))
                .andExpect(status().isBadRequest());
    }

    private static SupportClientProjection client(UUID id, Integer balance) {
        return new SupportClientProjection() {
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public String getUsername() {
                return "user_" + id;
            }

            @Override
            public Instant getRegisteredAt() {
                return Instant.ofEpochMilli(1789813263222L);
            }

            @Override
            public String getCallbackUrl() {
                return null;
            }

            @Override
            public Integer getOrderTimeoutSeconds() {
                return 900;
            }

            @Override
            public BigDecimal getCommissionPercent() {
                return null;
            }

            @Override
            public Integer getBalance() {
                return balance;
            }

            @Override
            public Set<RequestMethod> getMethods() {
                return Set.of();
            }

            @Override
            public String getComment() {
                return null;
            }

            @Override
            public Integer getMinWithdrawalAmount() {
                return null;
            }
        };
    }
}
