package net.rcetech.incidents;

import net.rcetech.domain.service.incidents.IncidentService;
import net.rcetech.meta.incidents.BalanceUpdateErrorEvent;
import net.rcetech.meta.incidents.DetailsSearchErrorEvent;
import net.rcetech.meta.incidents.IncidentEvent;
import net.rcetech.meta.incidents.IncidentType;
import net.rcetech.meta.incidents.StuckSearchOrderErrorEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class IncidentEventListenerTest {

    @Mock
    private IncidentService incidentService;

    @InjectMocks
    private IncidentEventListener incidentEventListener;

    static Stream<Arguments> events() {
        return Stream.of(
                Arguments.of(new BalanceUpdateErrorEvent(UUID.fromString("7d2b9e4c-1a6f-4e83-a5d0-9c3b8f6e2a17"),
                        UUID.fromString("f3f0d1d5-f4c6-4af5-ad9c-327ac365ea92"), 1_760_000_000_000L),
                        IncidentType.BALANCE_UPDATE_ERROR),
                Arguments.of(new StuckSearchOrderErrorEvent(UUID.fromString("3c8e1f5a-7b2d-4a9e-b6c0-4d1e8f2a5b97"),
                        "Connection is not available", 1_790_262_057_999L),
                        IncidentType.STUCK_SEARCH_ORDER_ERROR)
        );
    }

    @ParameterizedTest
    @MethodSource("events")
    @DisplayName("Метод должен сохранить инцидент с типом и временем ошибки из события.")
    void incident_shouldSaveIncident(IncidentEvent event, IncidentType type) {
        incidentEventListener.incident(event);

        verify(incidentService).save(type, event, event.errorTimestamp());
        verifyNoMoreInteractions(incidentService);
    }

    @ParameterizedTest
    @CsvSource({
            "7d2b9e4c-1a6f-4e83-a5d0-9c3b8f6e2a17,Неизвестная ошибка GRPC 14,1760000000000",
            "3c8e1f5a-7b2d-4a9e-b6c0-4d1e8f2a5b97,Непредвиденная ошибка: Connection refused,1790262057999"
    })
    @DisplayName("Метод должен сохранить инцидент об ошибке поиска реквизитов, только если нет необработанного такого типа.")
    void incident_shouldSaveIfNoUnresolved(UUID orderId, String error, long errorTimestamp) {
        DetailsSearchErrorEvent event = new DetailsSearchErrorEvent(orderId, error, errorTimestamp);

        incidentEventListener.incident(event);

        verify(incidentService).saveIfNoUnresolved(IncidentType.DETAILS_SEARCH_ERROR, event, errorTimestamp);
        verifyNoMoreInteractions(incidentService);
    }
}
