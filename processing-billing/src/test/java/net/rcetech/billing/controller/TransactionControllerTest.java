package net.rcetech.billing.controller;

import net.rcetech.domain.repository.billing.TransactionSpecification;
import net.rcetech.domain.service.billing.TransactionService;
import net.rcetech.meta.billing.Operation;
import net.rcetech.meta.billing.TransactionType;
import net.rcetech.meta.billing.dto.TransactionFilter;
import net.rcetech.meta.billing.dto.TransactionResponse;
import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.user.Role;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
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
class TransactionControllerTest {

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private TransactionService transactionService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void get_shouldNotHaveAccessForClient() throws Exception {
        mockMvc.perform(get("/api/support/transaction")
                .with(csrf())
                .with(user("a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7").roles(Role.CLIENT.name())))
                .andExpect(status().isForbidden());
    }

    @Test
    void createManualCorrection_shouldNotHaveAccessForClient() throws Exception {
        mockMvc.perform(post("/api/support/transaction")
                        .with(csrf())
                        .with(user("a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7").roles(Role.CLIENT.name()))
                )
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({
            "0c4f8a2e-6b1d-4e7a-9c3f-5d2b8e1a7f60,a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7,test1,1789813263222,CREDIT,3252,ORDER_CONFIRMATION," +
                    "Подтверждение ордера 123. Транзакция создана системой.",
            "e83b5d17-2a9c-4f6e-b1d4-7c0a3e9f5b28,20484949-7339-4e19-86c9-d1e2c492f75f,BOLIK,1789813269144,DEBIT,50664,MANUAL_CORRECT," +
                    "Подтверждение ордера 89723452. Транзакция создана системой."
    })
    void get_shouldReturnAllTransactions(UUID id, UUID clientId, String username, long millis, Operation operation,
                                         Integer amount, TransactionType type, String comment) throws Exception {
        TransactionResponse transactionResponse = mock(TransactionResponse.class);
        when(transactionResponse.id()).thenReturn(id);
        when(transactionResponse.clientId()).thenReturn(clientId);
        when(transactionResponse.clientUsername()).thenReturn(username);
        when(transactionResponse.createdAt()).thenReturn(Instant.ofEpochMilli(millis));
        when(transactionResponse.operation()).thenReturn(operation);
        when(transactionResponse.amount()).thenReturn(amount);
        when(transactionResponse.type()).thenReturn(type);
        when(transactionResponse.comment()).thenReturn(comment);
        when(transactionService.findAll(any(), any(), any())).thenReturn(new PageImpl<>(List.of(transactionResponse, transactionResponse)));
        mockMvc.perform(get("/api/support/transaction").with(user("user").roles(Role.ADMIN.name())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
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
            "5a3e9c1d-7f2b-4d80-a6e4-1b9c3f7d2e05,test1,1789813253222,1789813263222",
            "c1d7b4a9-0e5f-4c36-9b2a-8f4e6d1c3a70,smoki_lolik,1789813153222,1789813223222",
            "7e2f0a6c-3b9d-4a15-8c7e-2d5a9f0b6e41,test1,1789813253000,1789813263200"
    })
    void get_shouldPassFilterParameters(UUID id, String client, long millisFrom, long millisTo) throws Exception {
        when(transactionService.findAll(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
        try (MockedStatic<TransactionSpecification> transactionSpecification = Mockito.mockStatic(TransactionSpecification.class)) {
            mockMvc.perform(get("/api/support/transaction").with(user("user").roles(Role.OPERATOR.name()))
                    .queryParam("id", id.toString())
                    .queryParam("client", client)
                    .queryParam("createdAtFrom", String.valueOf(millisFrom))
                    .queryParam("createdAtTo", String.valueOf(millisTo))
            ).andExpect(status().isOk());
            ArgumentCaptor<TransactionFilter> captor = ArgumentCaptor.forClass(TransactionFilter.class);
            transactionSpecification.verify(() -> TransactionSpecification.matches(captor.capture()));
            TransactionFilter filter = captor.getValue();
            assertAll(
                    () -> assertEquals(id, filter.id()),
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
        mockMvc.perform(post("/api/support/transaction")
                        .with(csrf())
                        .with(
                                user("a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7")
                                        .roles(Role.ADMIN.name()))
                        .header("Content-Type", "application/json")
                        .content(json)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value(Matchers.containsString("Поле «clientId» должно быть заполнено.")));
    }

    @ValueSource(strings = {
            "{\"clientId\":\"a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7\"}",
            "{\"clientId\":\"a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7\",\"operation\":null}"
    })
    @ParameterizedTest
    void createManualCorrect_shouldReturn400IfOperationIsNull(String json) throws Exception {
        mockMvc.perform(post("/api/support/transaction")
                .with(csrf())
                .with(
                        user("a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7")
                                .roles(Role.ADMIN.name()))
                .header("Content-Type", "application/json")
                .content(json)
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value(Matchers.containsString("Поле «operation» должно быть заполнено.")));
    }

    @ValueSource(strings = {
            "{\"clientId\":\"a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7\",\"operation\":\"CREDIT\"}",
            "{\"clientId\":\"a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7\",\"operation\":\"DEBIT\",\"amount\":null}"
    })
    @ParameterizedTest
    void createManualCorrect_shouldReturn400IfAmountIsNull(String json) throws Exception {
        mockMvc.perform(post("/api/support/transaction")
                        .with(csrf())
                        .with(
                                user("a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7")
                                        .roles(Role.ADMIN.name()))
                        .header("Content-Type", "application/json")
                        .content(json)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value(Matchers.containsString("Поле «amount» должно быть заполнено.")));
    }

    @ValueSource(strings = {
            "{\"clientId\":\"a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7\",\"operation\":\"CREDIT\",\"amount\":-5135}",
            "{\"clientId\":\"a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7\",\"operation\":\"DEBIT\",\"amount\":-236646}"
    })
    @ParameterizedTest
    void createManualCorrect_shouldReturn400IfAmountIsNegative(String json) throws Exception {
        mockMvc.perform(post("/api/support/transaction")
                        .with(csrf())
                        .with(
                                user("a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7")
                                        .roles(Role.ADMIN.name()))
                        .header("Content-Type", "application/json")
                        .content(json)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value(Matchers.containsString("Поле «amount» должно быть больше или равно нулю.")));
    }

    @ValueSource(strings = {
            "{\"clientId\":\"a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7\",\"operation\":\"CREDIT\",\"amount\":51235}",
            "{\"clientId\":\"a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7\",\"operation\":\"DEBIT\",\"amount\":35235,\"comment\":null}",
            "{\"clientId\":\"a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7\",\"operation\":\"DEBIT\",\"amount\":35235,\"comment\":\"\"}",
            "{\"clientId\":\"a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7\",\"operation\":\"DEBIT\",\"amount\":35235,\"comment\":\"   \"}"
    })
    @ParameterizedTest
    void createManualCorrect_shouldReturn400IfCommentIsBlank(String json) throws Exception {
        mockMvc.perform(post("/api/support/transaction")
                        .with(csrf())
                        .with(
                                user("a7ff6a91-dfb2-4bcd-b687-2dee1acad5f7")
                                        .roles(Role.ADMIN.name()))
                        .header("Content-Type", "application/json")
                        .content(json)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value(Matchers.containsString("Поле «comment» должно быть заполнено.")));
    }
}