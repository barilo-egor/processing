package net.rcetech.meta.orders.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import net.rcetech.meta.orders.dto.*;

public interface OrderApi {

    /**
     * Создание нового заказа
     */
    OrderResponseDTO createOrder(@Valid @NotNull CreateOrderRequestDTO request);

    /**
     * Обновление статуса заказа
     */
    void updateOrderStatus(@Valid @NotNull UpdateOrderStatusRequestDTO request);

    /**
     * Получение фильтрованного списка заказов с пагинацией
     */
    OrdersPageResponseDTO getOrders(@Valid @NotNull GetOrdersFilterDTO filter);

}
