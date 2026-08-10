package rce.tech.ordersapi.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import rce.tech.ordersapi.dto.*;

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
