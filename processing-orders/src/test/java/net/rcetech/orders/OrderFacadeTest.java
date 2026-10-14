package net.rcetech.orders;

import jakarta.persistence.OptimisticLockException;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.service.incidents.IncidentService;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.config.MetaExecutorSpringConfig;
import net.rcetech.meta.incidents.IncidentType;
import net.rcetech.meta.incidents.dto.CallbackStatusNotResolvedErrorData;
import net.rcetech.meta.orders.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {OrderFacade.class, MetaExecutorSpringConfig.class})
class OrderFacadeTest {

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private IncidentService incidentService;

    @Autowired
    private OrderFacade orderFacade;

    @Test
    @DisplayName("Метод должен повторить подтверждение три раза, если было проброшено OptimisticLockException.")
    void routeOrder_shouldRepeatConfirmOrder() {
        doThrow(OptimisticLockException.class)
                .doThrow(OptimisticLockException.class)
                .doNothing()
                .when(orderService).confirm(any(), any());
        Order order = new Order();
        order.setId(UUID.randomUUID());

        orderFacade.routeOrder(OrderStatus.SUCCESS, order, "STATUS");

        verify(orderService, times(3)).confirm(any(), any());
    }

    @Test
    @DisplayName("Метод должен повторить отмену три раза, если было проброшено OptimisticLockException.")
    void routeOrder_shouldRepeatCancelOrder() {
        doThrow(OptimisticLockException.class)
                .doThrow(OptimisticLockException.class)
                .doNothing()
                .when(orderService).cancel(any(), any());
        Order order = new Order();
        order.setId(UUID.randomUUID());

        orderFacade.routeOrder(OrderStatus.CANCELED, order, "STATUS");

        verify(orderService, times(3)).cancel(any(), any());
    }

    @Test
    @DisplayName("Метод должен повторить таймаут три раза, если было проброшено OptimisticLockException.")
    void routeOrder_shouldRepeatTimeoutOrder() {
        doThrow(OptimisticLockException.class)
                .doThrow(OptimisticLockException.class)
                .doNothing()
                .when(orderService).timeout(any(), any());
        Order order = new Order();
        order.setId(UUID.randomUUID());

        orderFacade.routeOrder(OrderStatus.TIMEOUT, order, "STATUS");

        verify(orderService, times(3)).timeout(any(), any());
    }

    @Test
    @DisplayName("Метод должен повторить спор три раза, если было проброшено OptimisticLockException.")
    void routeOrder_shouldRepeatDisputeOrder() {
        doThrow(OptimisticLockException.class)
                .doThrow(OptimisticLockException.class)
                .doNothing()
                .when(orderService).dispute(any(), any());
        Order order = new Order();
        order.setId(UUID.randomUUID());

        orderFacade.routeOrder(OrderStatus.DISPUTE, order, "STATUS");

        verify(orderService, times(3)).dispute(any(), any());
    }

    @ParameterizedTest
    @CsvSource({
            "c2cbb845-7313-4106-a1a3-e9e4b328bbd9,PROCESSING",
            "fe4a4710-9c2e-4f04-b274-06802fbf2012,WAITING_FOR_PAYMENT"
    })
    @DisplayName("Метод должен создать инцидент, если для статуса ордера не определен маршрут.")
    void routeOrder_shouldCreateIncidentIfStatusNotResolved(UUID orderId, String merchantOrderStatus) {
        Order order = new Order();
        order.setId(orderId);
        long before = System.currentTimeMillis();

        orderFacade.routeOrder(OrderStatus.NEW, order, merchantOrderStatus);

        long after = System.currentTimeMillis();
        ArgumentCaptor<Object> dataCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Long> timestampCaptor = ArgumentCaptor.forClass(Long.class);
        verify(incidentService).save(eq(IncidentType.MERCHANT_CALLBACK_STATUS_NOT_RESOLVED),
                dataCaptor.capture(), timestampCaptor.capture());
        verifyNoInteractions(orderService);
        CallbackStatusNotResolvedErrorData data = assertInstanceOf(CallbackStatusNotResolvedErrorData.class,
                dataCaptor.getValue());
        Long timestamp = timestampCaptor.getValue();
        assertAll(
                () -> assertEquals(orderId, data.orderId()),
                () -> assertEquals(OrderStatus.NEW, data.orderStatus()),
                () -> assertEquals(merchantOrderStatus, data.merchantOrderStatus()),
                () -> assertNotNull(timestamp),
                () -> assertTrue(timestamp >= before && timestamp <= after)
        );
    }
}
