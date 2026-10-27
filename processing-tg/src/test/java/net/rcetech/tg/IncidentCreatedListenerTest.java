package net.rcetech.tg;

import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.incidents.IncidentCreatedEvent;
import net.rcetech.meta.incidents.IncidentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IncidentCreatedListenerTest {

    @Mock
    private MethodExecutor methodExecutor;

    @ParameterizedTest
    @CsvSource({
            "-1001234567890,https://processing.example.com,15,BALANCE_UPDATE_ERROR",
            "123456789,http://localhost:8081,7,DETAILS_SEARCH_ERROR"
    })
    @DisplayName("Слушатель должен отправить в чат инцидентов номер, тип инцидента и ссылку на раздел инцидентов.")
    void incidentCreated_shouldSendMessage(Long chatId, String baseUrl, Long id, IncidentType type) {
        IncidentCreatedListener listener = new IncidentCreatedListener(methodExecutor, chatId, baseUrl);

        listener.incidentCreated(new IncidentCreatedEvent(id, type));

        verify(methodExecutor).sendMessage(chatId, "Создан инцидент №" + id + ": " + type.getSummary() + ".\n"
                + baseUrl + "/support/dashboard?section=incidents");
    }

    @ParameterizedTest
    @CsvSource({"15,BALANCE_UPDATE_ERROR", "7,DETAILS_SEARCH_ERROR"})
    @DisplayName("Слушатель не должен бросать исключение, если сообщение не удалось отправить.")
    void incidentCreated_shouldNotThrowIfSendFailed(Long id, IncidentType type) {
        IncidentCreatedListener listener = new IncidentCreatedListener(methodExecutor, 123456789L,
                "https://processing.example.com");
        doThrow(new BaseException("Не удалось отправить сообщение")).when(methodExecutor)
                .sendMessage(anyLong(), anyString());

        assertDoesNotThrow(() -> listener.incidentCreated(new IncidentCreatedEvent(id, type)));
    }
}
