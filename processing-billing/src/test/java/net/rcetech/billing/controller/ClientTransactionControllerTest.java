package net.rcetech.billing.controller;

import net.rcetech.billing.utils.TransactionSpecification;
import net.rcetech.domain.service.billing.TransactionService;
import net.rcetech.meta.billing.Operation;
import net.rcetech.meta.billing.TransactionType;
import net.rcetech.meta.billing.dto.ClientTransactionFilter;
import net.rcetech.meta.billing.dto.ClientTransactionResponse;
import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.config.ProcessingConfigurationProperties;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClientTransactionController.class)
@Import({MetaSecurityConfig.class})
@EnableConfigurationProperties(ProcessingConfigurationProperties.class)
class ClientTransactionControllerTest {

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private TransactionService transactionService;

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {
            "ADMIN", "OPERATOR"
    })
    void get_shouldNotHaveAccessForNotClient(String role) throws Exception {
        mockMvc.perform(get("/api/v1/transaction")
                        .with(csrf())
                        .with(user("a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7").roles(role)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({
            "a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7,1789813263222,CREDIT,3252,ORDER_CONFIRMATION," +
                    "Подтверждение ордера 123. Транзакция создана системой.",
            "20484949-7339-4e19-86c9-d1e2c492f75f,1789813269144,DEBIT,50664,MANUAL_CORRECT," +
                    "Подтверждение ордера 89723452. Транзакция создана системой."
    })
    void get_shouldReturnAllTransactions(UUID clientId, long millis, Operation operation,
                                         Integer amount, TransactionType type, String comment) throws Exception {
        ClientTransactionResponse transactionResponse = mock(ClientTransactionResponse.class);
        when(transactionResponse.createdAt()).thenReturn(Instant.ofEpochMilli(millis));
        when(transactionResponse.operation()).thenReturn(operation);
        when(transactionResponse.amount()).thenReturn(amount);
        when(transactionResponse.type()).thenReturn(type);
        when(transactionResponse.comment()).thenReturn(comment);
        when(transactionService.findAll(any(), any(), any())).thenReturn(new PageImpl<>(List.of(transactionResponse, transactionResponse)));
        mockMvc.perform(get("/api/v1/transaction")
                        .with(user(clientId.toString()).roles("CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].clientId").doesNotExist())
                .andExpect(jsonPath("$.content[0].clientUsername").doesNotExist())
                .andExpect(jsonPath("$.content[0].createdAt").value(millis))
                .andExpect(jsonPath("$.content[0].operation").value(operation.name()))
                .andExpect(jsonPath("$.content[0].amount").value(amount))
                .andExpect(jsonPath("$.content[0].type").value(type.name()))
                .andExpect(jsonPath("$.content[0].comment").value(comment))
                .andExpect(jsonPath("$.content[1].amount").value(amount));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7,1789813253222,1789813263222",
            "adaddd97-b135-46ba-b61c-77c5a9bb0325,1789813153222,1789813223222",
            "88d8ff18-2879-4127-810c-996d8a185d8d,1789813253000,1789813263200"
    })
    void get_shouldPassFilterParameters(UUID clientId, long millisFrom, long millisTo) throws Exception {
        when(transactionService.findAll(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
        try (MockedStatic<TransactionSpecification> transactionSpecification = Mockito.mockStatic(TransactionSpecification.class)) {
            mockMvc.perform(get("/api/v1/transaction")
                    .queryParam("createdAtFrom", String.valueOf(millisFrom))
                    .queryParam("createdAtTo", String.valueOf(millisTo))
                    .with(user(clientId.toString()).roles("CLIENT"))
            ).andExpect(status().isOk());
            ArgumentCaptor<ClientTransactionFilter> captor = ArgumentCaptor.forClass(ClientTransactionFilter.class);
            transactionSpecification.verify(() -> TransactionSpecification.matches(eq(clientId), captor.capture()));
            ClientTransactionFilter filter = captor.getValue();
            assertAll(
                    () -> assertEquals(millisFrom, filter.createdAtFrom().toEpochMilli()),
                    () -> assertEquals(millisTo, filter.createdAtTo().toEpochMilli())
            );
        }
    }
}