package net.rcetech.orders.service;

import lombok.RequiredArgsConstructor;
import net.rcetech.meta.orders.dto.*;
import net.rcetech.orders.dto.OrderDTO;
import net.rcetech.orders.enums.OrderStatus;
import net.rcetech.orders.mapper.OrderMapper;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import net.rcetech.meta.orders.service.OrderApi;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderApiImpl implements OrderApi {

    private final OrderMapper orderMapper;

    private final OrderService orderService;

    @Override
    public OrderResponseDTO createOrder(CreateOrderRequestDTO request) {
        OrderDTO orderDTO = orderMapper.toDTO(request);
        OrderDTO savedOrder = orderService.create(orderDTO);
        return orderMapper.toOrderResponseDTO(savedOrder);
    }

    @Override
    public void updateOrderStatus(UpdateOrderStatusRequestDTO request) {
        orderService.updateStatus(
                request.id().toString(),
                request.clientId(),
                OrderStatus.valueOf(request.status())
        );
    }

    @Override
    public OrdersPageResponseDTO getOrders(GetOrdersFilterDTO filter) {
        PaginationParamsDTO pagination = filter.pagination();

        Page<OrderDTO> dtoPage = orderService.findOrders(
                orderMapper.buildFindSpecification(filter),
                pagination.page(),
                pagination.size(),
                pagination.sorters()
        );

        List<OrderResponseDTO> orderResponses = dtoPage.getContent().stream()
                .map(orderMapper::toOrderResponseDTO)
                .toList();

        return new OrdersPageResponseDTO(orderResponses, dtoPage.getTotalElements());
    }

}
