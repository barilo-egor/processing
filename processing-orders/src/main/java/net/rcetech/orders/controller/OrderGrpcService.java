package net.rcetech.orders.controller;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.grpc.server.service.GrpcService;
import net.rcetech.grpc.generated.*;
import net.rcetech.orders.dto.OrderDTO;
import net.rcetech.orders.enums.OrderStatus;
import net.rcetech.orders.mapper.OrderMapper;
import net.rcetech.orders.service.OrderService;

@GrpcService
@Slf4j
public class OrderGrpcService extends OrdersServiceGrpc.OrdersServiceImplBase {

    private final OrderMapper orderMapper;

    private final OrderService orderService;

    public OrderGrpcService(OrderMapper orderMapper, OrderService orderService) {
        this.orderMapper = orderMapper;
        this.orderService = orderService;
    }

    @Override
    public void createOrder(CreateOrderGrpc request, StreamObserver<CreateOrderResponseGrpc> responseObserver) {
        OrderDTO orderDTO = orderMapper.toDTO(request);
        OrderDTO savedOrder = orderService.create(orderDTO);

        responseObserver.onNext(orderMapper.createOrderResponseGrpc(savedOrder));
        responseObserver.onCompleted();
    }

    @Override
    public void updateOrderStatus(UpdateOrderStatusGrpc request, StreamObserver<Empty> responseObserver) {
        Long clientId = request.hasClientId() ? request.getClientId() : null;
        orderService.updateStatus(request.getId(), clientId, OrderStatus.valueOf(request.getStatus()));

        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void getOrders(GetOrdersGrpc request, StreamObserver<GetOrdersResponseGrpc> responseObserver) {
        PaginationParams pagination = request.getPagination();

        Page<OrderDTO> dtoPage = orderService.findOrders(orderMapper.buildFindSpecification(request),
                pagination.getPage(),
                pagination.getSize(),
                pagination.getSortersList().stream().toList());

        GetOrdersResponseGrpc response = GetOrdersResponseGrpc.newBuilder()
                .addAllOrders(dtoPage.getContent().stream().map(orderMapper::toOrderResponse).toList())
                .setTotalElements(dtoPage.getTotalElements())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

}
