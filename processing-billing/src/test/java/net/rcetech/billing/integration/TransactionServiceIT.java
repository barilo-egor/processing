package net.rcetech.billing.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import net.rcetech.billing.dto.CreateTransactionRequest;
import net.rcetech.billing.dto.GetTransactionsResponse;
import net.rcetech.billing.entity.Transaction;
import net.rcetech.billing.enums.Operation;
import net.rcetech.billing.enums.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TransactionRestControllerIT extends BaseIntegrationTest {

    private final TimeBasedEpochGenerator generator = Generators.timeBasedEpochGenerator();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String BASE_URL = "/transactions";

    @Test
    @DisplayName("Создание transaction с валидными данными и проверка защиты от дубликатов (existsById)")
    void createClient_Success_And_IgnoreDuplicate() throws Exception {
        UUID transactionId = generator.generate();
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .id(transactionId)
                .clientId(1L)
                .amount(333)
                .operation(Operation.CREDIT.name())
                .type(TransactionType.CLIENT_WITHDRAWAL.name())
                .build();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        Optional<Transaction> savedTransaction = transactionRepository.findById(transactionId);
        assertTrue(savedTransaction.isPresent(), "Транзакция должна быть сохранена при первом вызове");
        assertEquals(savedTransaction.get().getClientId(), request.clientId());
        assertEquals(savedTransaction.get().getAmount(), request.amount());
        assertEquals(savedTransaction.get().getOperation(), Operation.valueOf(request.operation()));
        assertEquals(savedTransaction.get().getType(), TransactionType.valueOf(request.type()));

        Instant firstCallCreatedAt = savedTransaction.get().getCreatedAt();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        Optional<Transaction> transactionAfterSecondCall = transactionRepository.findById(transactionId);
        assertTrue(transactionAfterSecondCall.isPresent());

        assertEquals(
                firstCallCreatedAt,
                transactionAfterSecondCall.get().getCreatedAt(),
                "Транзакция не должна быть перезаписана или изменена при повторном вызове"
        );
    }

    @Test
    @DisplayName("Создание transaction с невалидными данными — проверка валидации")
    void createTransaction_ValidationError_ShouldReturnDetailedBadRequest() throws Exception {
        CreateTransactionRequest invalidRequest = CreateTransactionRequest.builder()
                .id(UUID.randomUUID())
                .clientId(0L)
                .amount(333)
                .operation("")
                .type("CLIENT_WITHDRAWAL")
                .build();

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Успешное получение списка транзакций с пагинацией")
    void getTransactions_Success() throws Exception {
        UUID transactionId = UUID.randomUUID();
        Instant now = Instant.now();

        Transaction testTransaction = Transaction.builder()
                .id(transactionId)
                .clientId(42L)
                .amount(1500)
                .operation(Operation.CREDIT)
                .type(TransactionType.CLIENT_WITHDRAWAL)
                .comment("Test history query")
                .createdAt(now)
                .build();

        transactionRepository.save(testTransaction);

        var result = mockMvc.perform(get(BASE_URL)
                        .param("page", "0")
                        .param("size", "10")
                        .param("clientIds", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.transactions").isArray())
                .andExpect(jsonPath("$.transactions[0].id").value(transactionId.toString()))
                .andExpect(jsonPath("$.transactions[0].clientId").value(42))
                .andExpect(jsonPath("$.transactions[0].amount").value(1500))
                .andExpect(jsonPath("$.transactions[0].operation").value(Operation.CREDIT.name()))
                .andExpect(jsonPath("$.transactions[0].type").value(TransactionType.CLIENT_WITHDRAWAL.name()))
                .andExpect(jsonPath("$.transactions[0].comment").value("Test history query"))
                .andReturn();

        GetTransactionsResponse responseBody = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                GetTransactionsResponse.class
        );

        assertThat(responseBody.transactions()).hasSize(1);
    }

    @Test
    @DisplayName("Успешное получение списка транзакций без пагинации")
    void getTransactions_Success_No_Pagination() throws Exception {
        UUID transactionId = UUID.randomUUID();
        Instant now = Instant.now();

        Transaction testTransaction = Transaction.builder()
                .id(transactionId)
                .clientId(42L)
                .amount(1500)
                .operation(Operation.CREDIT)
                .type(TransactionType.CLIENT_WITHDRAWAL)
                .comment("Test history query")
                .createdAt(now)
                .build();

        transactionRepository.save(testTransaction);

        mockMvc.perform(get(BASE_URL)
                        .param("clientIds", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.transactions[0].id").value(transactionId.toString()))
                .andExpect(jsonPath("$.transactions[0].clientId").value(42))
                .andExpect(jsonPath("$.transactions[0].amount").value(1500))
                .andExpect(jsonPath("$.transactions[0].operation").value(Operation.CREDIT.name()))
                .andExpect(jsonPath("$.transactions[0].type").value(TransactionType.CLIENT_WITHDRAWAL.name()))
                .andExpect(jsonPath("$.transactions[0].comment").value("Test history query"));
    }

}
