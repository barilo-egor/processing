package net.rcetech.orders;

import jakarta.persistence.OptimisticLockException;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.config.MetaExecutorSpringConfig;
import net.rcetech.meta.orders.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {OrderFacade.class, MetaExecutorSpringConfig.class})
class OrderFacadeTest {

    @MockitoBean
    private OrderService orderService;

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

}