package net.rcetech.billing.integration;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import com.google.protobuf.Timestamp;
import com.google.rpc.BadRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tgb.cryptoexchange.grpc.generated.*;
import net.rcetech.billing.entity.Transaction;
import net.rcetech.billing.enums.Operation;
import net.rcetech.billing.enums.TransactionType;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class TransactionServiceIT extends BaseIntegrationTest {

    private final TimeBasedEpochGenerator generator = Generators.timeBasedEpochGenerator();

    private TransactionsServiceGrpc.TransactionsServiceBlockingStub blockingStub;

    @BeforeEach
    void setup() {
        blockingStub = TransactionsServiceGrpc.newBlockingStub(channel);
    }

    @Test
    @DisplayName("Создание transaction с валидными данными и проверка защиты от дубликатов (existsById)")
    void createClient_Success_And_IgnoreDuplicate() {
        UUID transactionId = generator.generate();
        CreateTransactionGrpc request = CreateTransactionGrpc.newBuilder()
                .setId(transactionId.toString())
                .setClientId(1L)
                .setAmount(333)
                .setOperation(Operation.CREDIT.name())
                .setType(TransactionType.CLIENT_WITHDRAWAL.name())
                .build();

        var response = blockingStub.createTransaction(request);

        Optional<Transaction> savedTransaction = transactionRepository.findById(transactionId);
        assertThat(response).isNotNull();
        assertTrue(savedTransaction.isPresent(), "Транзакция должна быть сохранена при первом вызове");
        assertEquals(savedTransaction.get().getClientId(), request.getClientId());
        assertEquals(savedTransaction.get().getAmount(), request.getAmount());
        assertEquals(savedTransaction.get().getOperation(), Operation.valueOf(request.getOperation()));
        assertEquals(savedTransaction.get().getType(), TransactionType.valueOf(request.getType()));

        Instant firstCallCreatedAt = savedTransaction.get().getCreatedAt();

        assertDoesNotThrow(
                () -> blockingStub.createTransaction(request),
                "Повторный запрос не должен вызывать исключений, сервис должен проигнорировать дубликат"
        );

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
    void createTransaction_ValidationError_ShouldReturnDetailedBadRequest() {
        CreateTransactionGrpc invalidRequest = CreateTransactionGrpc.newBuilder()
                .setId("not-a-valid-uuid")
                .setClientId(0L)
                .setAmount(333)
                .setOperation("")
                .setType("CLIENT_WITHDRAWAL")
                .build();

        StatusRuntimeException exception = Assertions.assertThrows(
                StatusRuntimeException.class,
                () -> blockingStub.createTransaction(invalidRequest),
                "Ожидалось исключение StatusRuntimeException из-за ошибок валидации"
        );

        assertEquals(Status.Code.INVALID_ARGUMENT, exception.getStatus().getCode());
        assertEquals("INVALID_ARGUMENT: Bad request.", exception.getMessage());

        com.google.rpc.Status statusProto = StatusProto.fromThrowable(exception);
        Assertions.assertNotNull(statusProto, "StatusProto не должен быть null");
        assertEquals("Bad request.", statusProto.getMessage());

        assertTrue(statusProto.getDetailsCount() > 0, "Детали ошибки должны присутствовать в ответе");

        try {
            BadRequest badRequest = statusProto.getDetails(0).unpack(BadRequest.class);
            Assertions.assertFalse(badRequest.getFieldViolationsList().isEmpty(),
                    "Список нарушений полей не должен быть пустым");

            boolean hasIdError = badRequest.getFieldViolationsList().stream()
                    .anyMatch(v -> v.getField().equals("id"));
            boolean hasClientIdError = badRequest.getFieldViolationsList().stream()
                    .anyMatch(v -> v.getField().equals("client_id"));
            boolean hasOperationError = badRequest.getFieldViolationsList().stream()
                    .anyMatch(v -> v.getField().equals("operation"));

            assertTrue(hasIdError, "Должна быть ошибка валидации для поля 'id'");
            assertTrue(hasClientIdError, "Должна быть ошибка валидации для поля 'client_id'");
            assertTrue(hasOperationError, "Должна быть ошибка валидации для поля 'operation'");

        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            Assertions.fail("Ошибка при распаковке Any в BadRequest: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Успешное получение списка транзакций с пагинацией")
    void getTransactions_Success() {
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

        PaginationParams pagination = PaginationParams.newBuilder()
                .setPage(0)
                .setSize(10)
                .build();

        GetTransactionsGrpc request = GetTransactionsGrpc.newBuilder()
                .setPagination(pagination)
                .addClientIds(42L)
                .build();

        GetTransactionsResponseGrpc response = blockingStub.getTransactions(request);

        Assertions.assertNotNull(response, "Ответ gRPC не должен быть null");
        assertTrue(response.getTotalElements() >= 1, "Общее количество элементов должно быть >= 1");
        Assertions.assertFalse(response.getTransactionsList().isEmpty(), "Список транзакций не должен быть пустым");

        TransactionResponse mappedResponse = response.getTransactionsList().stream()
                .filter(t -> t.getId().equals(transactionId.toString()))
                .findFirst()
                .orElseGet(() -> Assertions.fail("Сохраненная транзакция не найдена в gRPC ответе"));

        assertEquals(42L, mappedResponse.getClientId());
        assertEquals(1500, mappedResponse.getAmount());
        assertEquals(Operation.CREDIT.name(), mappedResponse.getOperation());
        assertEquals(TransactionType.CLIENT_WITHDRAWAL.name(), mappedResponse.getType());
        assertEquals("Test history query", mappedResponse.getComment());

        Timestamp createdAtTimestamp = mappedResponse.getCreatedAt();
        Assertions.assertNotNull(createdAtTimestamp, "Временная метка не должна быть null");
        assertEquals(now.getEpochSecond(), createdAtTimestamp.getSeconds());
    }

    @Test
    @DisplayName("Успешное получение списка транзакций без пагинации")
    void getTransactions_Success_No_Pagination() {
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

        GetTransactionsGrpc request = GetTransactionsGrpc.newBuilder()
                .addClientIds(42L)
                .build();

        GetTransactionsResponseGrpc response = blockingStub.getTransactions(request);

        Assertions.assertNotNull(response, "Ответ gRPC не должен быть null");
        assertTrue(response.getTotalElements() >= 1, "Общее количество элементов должно быть >= 1");
        Assertions.assertFalse(response.getTransactionsList().isEmpty(), "Список транзакций не должен быть пустым");

        TransactionResponse mappedResponse = response.getTransactionsList().stream()
                .filter(t -> t.getId().equals(transactionId.toString()))
                .findFirst()
                .orElseGet(() -> Assertions.fail("Сохраненная транзакция не найдена в gRPC ответе"));

        assertEquals(42L, mappedResponse.getClientId());
        assertEquals(1500, mappedResponse.getAmount());
        assertEquals(Operation.CREDIT.name(), mappedResponse.getOperation());
        assertEquals(TransactionType.CLIENT_WITHDRAWAL.name(), mappedResponse.getType());
        assertEquals("Test history query", mappedResponse.getComment());

        Timestamp createdAtTimestamp = mappedResponse.getCreatedAt();
        Assertions.assertNotNull(createdAtTimestamp, "Временная метка не должна быть null");
        assertEquals(now.getEpochSecond(), createdAtTimestamp.getSeconds());
    }

}
