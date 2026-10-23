package net.rcetech.incidents;

import net.rcetech.domain.service.incidents.IncidentService;
import net.rcetech.incidents.handler.DefaultIncidentHandler;
import net.rcetech.incidents.handler.IncidentHandler;
import net.rcetech.incidents.handler.OrderIncidentHandler;
import net.rcetech.meta.incidents.*;
import net.rcetech.meta.orders.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class IncidentEventListenerTest {

    @Mock
    private IncidentService incidentService;

    private IncidentEventListener incidentEventListener;

    @BeforeEach
    void setUp() {
        List<IncidentHandler> handlers = new ArrayList<>(List.of(
                new DefaultIncidentHandler(incidentService),
                new OrderIncidentHandler(incidentService)
        ));
        AnnotationAwareOrderComparator.sort(handlers);
        incidentEventListener = new IncidentEventListener(handlers);
    }

    static Stream<Arguments> events() {
        return Stream.of(
                Arguments.of(new BalanceUpdateErrorEvent(UUID.fromString("7d2b9e4c-1a6f-4e83-a5d0-9c3b8f6e2a17"),
                        UUID.fromString("f3f0d1d5-f4c6-4af5-ad9c-327ac365ea92"), 1_760_000_000_000L),
                        IncidentType.BALANCE_UPDATE_ERROR),
                Arguments.of(new TransactionCreationErrorEvent(UUID.fromString("4f8a2c6e-9b1d-4e73-b5a0-6c2e8d4f1b39"),
                        OrderStatus.SUCCESS, 1_790_262_057_999L), IncidentType.TRANSACTION_CREATION_ERROR)
        );
    }

    static Stream<Arguments> onlyIfNoUnresolvedEvents() {
        return Stream.of(
                Arguments.of(new DetailsSearchErrorEvent(UUID.fromString("7d2b9e4c-1a6f-4e83-a5d0-9c3b8f6e2a17"),
                        "Неизвестная ошибка GRPC 14", 1_760_000_000_000L), IncidentType.DETAILS_SEARCH_ERROR),
                Arguments.of(new DetailsSearchErrorEvent(UUID.fromString("3c8e1f5a-7b2d-4a9e-b6c0-4d1e8f2a5b97"),
                        "Непредвиденная ошибка: Connection refused", 1_790_262_057_999L),
                        IncidentType.DETAILS_SEARCH_ERROR),
                Arguments.of(new AsyncDetailsSearchErrorEvent(UUID.fromString("9a4c2e7b-5d1f-4b38-8e6a-2f7c9d0b3e51"),
                        "Deadlock found", 1_791_100_000_000L), IncidentType.ASYNC_DETAILS_SEARCH_ERROR),
                Arguments.of(new AsyncDetailsSearchErrorEvent(UUID.fromString("b2e8d4f1-6c3a-4e97-9d05-7a1f3c8e6b24"),
                        "Connection is not available", 1_792_400_000_000L), IncidentType.ASYNC_DETAILS_SEARCH_ERROR)
        );
    }

    static Stream<Arguments> onlyIfNoUnresolvedForOrderEvents() {
        return Stream.of(
                Arguments.of(new StuckSearchOrderErrorEvent(UUID.fromString("3c8e1f5a-7b2d-4a9e-b6c0-4d1e8f2a5b97"),
                        "Connection is not available", 1_790_262_057_999L), IncidentType.STUCK_SEARCH_ORDER_ERROR),
                Arguments.of(new StuckSearchOrderErrorEvent(UUID.fromString("e5b1c9d3-2f7a-4c86-a0e4-8d3b6f1a9c72"),
                        "Deadlock found", 1_793_500_000_000L), IncidentType.STUCK_SEARCH_ORDER_ERROR),
                Arguments.of(new OrderExpirationErrorEvent(UUID.fromString("6f1d3b8a-4c2e-4a95-b7d0-1e9c5a3f8b62"),
                        "Connection is not available", 1_791_800_000_000L), IncidentType.ORDER_EXPIRATION_ERROR),
                Arguments.of(new OrderExpirationErrorEvent(UUID.fromString("c4a7e2d9-8b3f-4e16-9a5c-3d2b7f0e1a84"),
                        "Deadlock found", 1_794_200_000_000L), IncidentType.ORDER_EXPIRATION_ERROR)
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
    @MethodSource("onlyIfNoUnresolvedEvents")
    @DisplayName("Метод должен сохранить инцидент об ошибке поиска реквизитов, только если нет необработанного такого типа.")
    void incident_shouldSaveIfNoUnresolved(IncidentEvent event, IncidentType type) {
        incidentEventListener.incident(event);

        verify(incidentService).saveIfNoUnresolved(type, event, event.errorTimestamp());
        verifyNoMoreInteractions(incidentService);
    }

    @ParameterizedTest
    @MethodSource("onlyIfNoUnresolvedForOrderEvents")
    @DisplayName("Метод должен сохранить инцидент по ордеру, только если нет необработанного такого типа по этому ордеру.")
    void incident_shouldSaveIfNoUnresolvedForOrder(OrderIncidentEvent event, IncidentType type) {
        incidentEventListener.incident(event);

        verify(incidentService).saveIfNoUnresolvedForOrder(type, event.orderId(), event, event.errorTimestamp());
        verifyNoMoreInteractions(incidentService);
    }
}
