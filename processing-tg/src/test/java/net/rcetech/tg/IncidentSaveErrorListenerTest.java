package net.rcetech.tg;

import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.incidents.IncidentSaveErrorEvent;
import net.rcetech.meta.incidents.IncidentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IncidentSaveErrorListenerTest {

    @Mock
    private MethodExecutor methodExecutor;

    @ParameterizedTest
    @CsvSource({
            "-1001234567890,BALANCE_UPDATE_ERROR,clientId,5f0c7a52-3b1e-4d8a-9c6f-1a2b3c4d5e6f,1760000000000",
            "123456789,DETAILS_SEARCH_ERROR,orderId,9e8d7c6b-5a49-4382-a1b0-c9d8e7f6a5b4,1790262057999"
    })
    @DisplayName("Слушатель должен отправить в чат тип инцидента, его данные и время ошибки сохранения.")
    void incidentSaveError_shouldSendMessage(Long chatId, IncidentType type, String key, String value,
                                             long errorTimestamp) {
        IncidentSaveErrorListener listener = new IncidentSaveErrorListener(methodExecutor, chatId);

        listener.incidentSaveError(new IncidentSaveErrorEvent(type, Map.of(key, value), errorTimestamp));

        verify(methodExecutor).sendMessage(chatId, "Не удалось сохранить инцидент " + type.name() + ": "
                + type.getSummary() + ".\nВремя ошибки: " + errorTimestamp + "\nДанные: {" + key + "=" + value + "}");
    }

    @ParameterizedTest
    @CsvSource({"BALANCE_UPDATE_ERROR,данные 1,1760000000000", "DETAILS_SEARCH_ERROR,данные 2,1790262057999"})
    @DisplayName("Слушатель не должен бросать исключение, если сообщение не удалось отправить.")
    void incidentSaveError_shouldNotThrowIfSendFailed(IncidentType type, String data, long errorTimestamp) {
        IncidentSaveErrorListener listener = new IncidentSaveErrorListener(methodExecutor, 123456789L);
        doThrow(new BaseException("Не удалось отправить сообщение")).when(methodExecutor)
                .sendMessage(anyLong(), anyString());

        assertDoesNotThrow(() -> listener.incidentSaveError(new IncidentSaveErrorEvent(type, data, errorTimestamp)));
    }
}
