package net.rcetech.billing.controller;

import net.rcetech.domain.repository.billing.TransactionSpecification;
import net.rcetech.domain.service.billing.TransactionService;
import net.rcetech.meta.billing.Operation;
import net.rcetech.meta.billing.TransactionType;
import net.rcetech.meta.billing.dto.TransactionFilter;
import net.rcetech.meta.billing.dto.TransactionResponse;
import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.config.ProcessingConfigurationProperties;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@Import({MetaSecurityConfig.class})
@EnableConfigurationProperties(ProcessingConfigurationProperties.class)
class TransactionControllerTest {

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private TransactionService transactionService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void get_shouldNotHaveAccessForClient() throws Exception {
        mockMvc.perform(get("/api/private/transaction")
                .with(csrf())
                .with(user("a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7").roles("CLIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createManualCorrection_shouldNotHaveAccessForClient() throws Exception {
        mockMvc.perform(post("/api/private/transaction")
                        .with(csrf())
                        .with(user("a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7").roles("CLIENT"))
                )
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({
            "a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7,test1,1789813263222,CREDIT,3252,ORDER_CONFIRMATION," +
                    "Подтверждение ордера 123. Транзакция создана системой.",
            "20484949-7339-4e19-86c9-d1e2c492f75f,BOLIK,1789813269144,DEBIT,50664,MANUAL_CORRECT," +
                    "Подтверждение ордера 89723452. Транзакция создана системой."
    })
    @WithMockUser(roles = {"ADMIN"})
    void get_shouldReturnAllTransactions(UUID clientId, String username, long millis, Operation operation,
                                         Integer amount, TransactionType type, String comment) throws Exception {
        TransactionResponse transactionResponse = mock(TransactionResponse.class);
        when(transactionResponse.clientId()).thenReturn(clientId);
        when(transactionResponse.clientUsername()).thenReturn(username);
        when(transactionResponse.createdAt()).thenReturn(Instant.ofEpochMilli(millis));
        when(transactionResponse.operation()).thenReturn(operation);
        when(transactionResponse.amount()).thenReturn(amount);
        when(transactionResponse.type()).thenReturn(type);
        when(transactionResponse.comment()).thenReturn(comment);
        when(transactionService.findAll(any(), any(), any())).thenReturn(new PageImpl<>(List.of(transactionResponse, transactionResponse)));
        mockMvc.perform(get("/api/private/transaction"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].clientId").value(clientId.toString()))
                .andExpect(jsonPath("$.content[0].clientUsername").value(username))
                .andExpect(jsonPath("$.content[0].createdAt").value(millis))
                .andExpect(jsonPath("$.content[0].operation").value(operation.name()))
                .andExpect(jsonPath("$.content[0].amount").value(amount))
                .andExpect(jsonPath("$.content[0].type").value(type.name()))
                .andExpect(jsonPath("$.content[0].comment").value(comment))
                .andExpect(jsonPath("$.content[1].clientId").value(clientId.toString()));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "test1,1789813253222,1789813263222",
            "smoki_lolik,1789813153222,1789813223222",
            "test1,1789813253000,1789813263200"
    })
    @WithMockUser(roles = {"OPERATOR"})
    void get_shouldPassFilterParameters(String client, long millisFrom, long millisTo) throws Exception {
        when(transactionService.findAll(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
        try (MockedStatic<TransactionSpecification> transactionSpecification = Mockito.mockStatic(TransactionSpecification.class)) {
            mockMvc.perform(get("/api/private/transaction")
                    .queryParam("client", client)
                    .queryParam("createdAtFrom", String.valueOf(millisFrom))
                    .queryParam("createdAtTo", String.valueOf(millisTo))
            ).andExpect(status().isOk());
            ArgumentCaptor<TransactionFilter> captor = ArgumentCaptor.forClass(TransactionFilter.class);
            transactionSpecification.verify(() -> TransactionSpecification.matches(captor.capture()));
            TransactionFilter filter = captor.getValue();
            assertAll(
                    () -> assertEquals(client, filter.client()),
                    () -> assertEquals(millisFrom, filter.createdAtFrom().toEpochMilli()),
                    () -> assertEquals(millisTo, filter.createdAtTo().toEpochMilli())
            );
        }
    }

    @ValueSource(strings = {
            "{}",
            "{\"clientId\":null}"
    })
    @ParameterizedTest
    void createManualCorrect_shouldReturn400IfClientIdIsNull(String json) throws Exception {
        mockMvc.perform(post("/api/private/transaction")
                        .with(csrf())
                        .with(
                                user("a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7")
                                        .roles("ADMIN"))
                        .header("Content-Type", "application/json")
                        .content(json)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value(Matchers.containsString("field 'clientId' should not be null")));
    }

    @ValueSource(strings = {
            "{\"clientId\":\"a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7\"}",
            "{\"clientId\":\"a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7\",\"operation\":null}"
    })
    @ParameterizedTest
    void createManualCorrect_shouldReturn400IfOperationIsNull(String json) throws Exception {
        mockMvc.perform(post("/api/private/transaction")
                .with(csrf())
                .with(
                        user("a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7")
                                .roles("ADMIN"))
                .header("Content-Type", "application/json")
                .content(json)
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value(Matchers.containsString("field 'operation' should not be null")));
    }

    @ValueSource(strings = {
            "{\"clientId\":\"a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7\",\"operation\":\"CREDIT\"}",
            "{\"clientId\":\"a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7\",\"operation\":\"DEBIT\",\"amount\":null}"
    })
    @ParameterizedTest
    void createManualCorrect_shouldReturn400IfAmountIsNull(String json) throws Exception {
        mockMvc.perform(post("/api/private/transaction")
                        .with(csrf())
                        .with(
                                user("a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7")
                                        .roles("ADMIN"))
                        .header("Content-Type", "application/json")
                        .content(json)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value(Matchers.containsString("field 'amount' should not be null")));
    }

    @ValueSource(strings = {
            "{\"clientId\":\"a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7\",\"operation\":\"CREDIT\",\"amount\":-5135}",
            "{\"clientId\":\"a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7\",\"operation\":\"DEBIT\",\"amount\":-236646}"
    })
    @ParameterizedTest
    void createManualCorrect_shouldReturn400IfAmountIsNegative(String json) throws Exception {
        mockMvc.perform(post("/api/private/transaction")
                        .with(csrf())
                        .with(
                                user("a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7")
                                        .roles("ADMIN"))
                        .header("Content-Type", "application/json")
                        .content(json)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value(Matchers.containsString("field 'amount' should be positive or zero")));
    }

    @ValueSource(strings = {
            "{\"clientId\":\"a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7\",\"operation\":\"CREDIT\",\"amount\":51235}",
            "{\"clientId\":\"a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7\",\"operation\":\"DEBIT\",\"amount\":35235,\"comment\":null}",
            "{\"clientId\":\"a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7\",\"operation\":\"DEBIT\",\"amount\":35235,\"comment\":\"\"}",
            "{\"clientId\":\"a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7\",\"operation\":\"DEBIT\",\"amount\":35235,\"comment\":\"   \"}"
    })
    @ParameterizedTest
    void createManualCorrect_shouldReturn400IfCommentIsBlank(String json) throws Exception {
        mockMvc.perform(post("/api/private/transaction")
                        .with(csrf())
                        .with(
                                user("a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7")
                                        .roles("ADMIN"))
                        .header("Content-Type", "application/json")
                        .content(json)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value(Matchers.containsString("field 'comment' should not be blank")));
    }
}