package tgb.cryptoexchange.processingbilling.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tgb.cryptoexchange.processingbilling.dto.TransactionDTO;
import tgb.cryptoexchange.processingbilling.kafka.TransactionConsumer;
import tgb.cryptoexchange.processingbilling.service.TransactionService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionConsumerTest {

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private TransactionConsumer transactionConsumer;

    @Test
    @DisplayName("Успешный сценарий — вызов transactionService.save()")
    void saveTransaction_ShouldCallSave_WhenValidDtoReceived() {
        UUID transactionId = UUID.randomUUID();
        TransactionDTO dto = TransactionDTO.builder()
                .id(transactionId)
                .clientId(42L)
                .amount(500)
                .build();
        String key = "test-kafka-key";

        assertDoesNotThrow(() -> transactionConsumer.saveTransaction(dto, key),
                "Метод консьюмера не должен выбрасывать исключений при успешной обработке");

        verify(transactionService, times(1)).save(dto);
    }

    @Test
    @DisplayName("Сценарий с ошибкой — исключение из сервиса перехватывается и логируется")
    void saveTransaction_ShouldCatchAndLogException_WhenServiceFails() {
        UUID transactionId = UUID.randomUUID();
        TransactionDTO dto = TransactionDTO.builder()
                .id(transactionId)
                .build();
        String key = "test-kafka-key";

        doThrow(new RuntimeException("Database timeout error"))
                .when(transactionService).save(dto);

        assertDoesNotThrow(() -> transactionConsumer.saveTransaction(dto, key),
                "Исключение должно быть перехвачено внутри метода и не выходить в вызывающий поток");

        verify(transactionService, times(1)).save(dto);
    }

}