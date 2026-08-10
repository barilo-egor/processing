package net.rcetech.processingdetailsapi.service;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.Empty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import tgb.cryptoexchange.grpc.generated.*;
import net.rcetech.processingdetailsapi.dto.ApiOrdersCreateRequestDTO;
import net.rcetech.processingdetailsapi.dto.ApiOrdersResponseDTO;
import net.rcetech.processingdetailsapi.exceptions.OrderNotFoundException;
import net.rcetech.processingdetailsapi.mapper.OrdersMapper;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class ApiOrdersGrpcService extends GrpcService {

    private final OrdersServiceGrpc.OrdersServiceFutureStub ordersFutureStub;

    private final OrdersMapper ordersMapper;

    public ApiOrdersGrpcService(OrdersMapper ordersMapper,
            OrdersServiceGrpc.OrdersServiceFutureStub ordersFutureStub) {
        this.ordersFutureStub = ordersFutureStub;
        this.ordersMapper = ordersMapper;
    }

    /**
     * Отправляет запрос на создание нового заказа через gRPC.
     *
     * @param createRequestDTO параметры создаваемого заказа.
     * @return {@link ApiOrdersResponseDTO} с метаданными и статусом созданного заказа.
     */
    public ApiOrdersResponseDTO createOrder(ApiOrdersCreateRequestDTO createRequestDTO) {
        CreateOrderGrpc request = ordersMapper.createOrderGrpc(createRequestDTO);
        ListenableFuture<CreateOrderResponseGrpc> grpcFuture = ordersFutureStub.createOrder(request);
        CreateOrderResponseGrpc response = toCompletableFuture(grpcFuture).join();
        return ordersMapper.grpcResponseToDTO(response);
    }

    /**
     * Ищет заказ через gRPC последовательно по системному, затем по внешнему ID.
     *
     * @param id       системный или внешний идентификатор заказа.
     * @param clientId идентификатор клиента.
     * @return {@link ApiOrdersResponseDTO} с деталями найденного заказа.
     * @throws OrderNotFoundException если заказ не найден ни по одному из идентификаторов.
     */
    public ApiOrdersResponseDTO getOrders(String id, Long clientId) {
        return findByRequest(ordersMapper.getOrdersByIdGrpc(id, clientId))
                .or(() -> findByRequest(ordersMapper.getOrdersByExternalIdGrpc(id, clientId)))
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    /**
     * Запрашивает список заказов клиента с поддержкой пагинации и сортировки через gRPC.
     *
     * @param clientId идентификатор клиента.
     * @param pageable параметры пагинации и сортировки.
     * @return список {@link ApiOrdersResponseDTO} с найденными заказами.
     */
    public List<ApiOrdersResponseDTO> findOrders(Long clientId, Pageable pageable) {
        GetOrdersGrpc request = ordersMapper.getOrdersGrpc(clientId, pageable);
        ListenableFuture<GetOrdersResponseGrpc> grpcFuture = ordersFutureStub.getOrders(request);
        GetOrdersResponseGrpc response = toCompletableFuture(grpcFuture).join();
        return response.getOrdersList().stream().map(ordersMapper::getOrder).toList();
    }

    private Optional<ApiOrdersResponseDTO> findByRequest(GetOrdersGrpc request) {
        ListenableFuture<GetOrdersResponseGrpc> grpcFuture = ordersFutureStub.getOrders(request);
        GetOrdersResponseGrpc response = toCompletableFuture(grpcFuture).join();
        long total = response.getTotalElements();
        if (total > 0) {
            return response.getOrdersList().stream().map(ordersMapper::getOrder).findFirst();
        }
        return Optional.empty();
    }

    /**
     * Отправляет gRPC-запрос на перевод статуса заказа в состояние CANCELED.
     *
     * @param id       идентификатор отменяемого заказа.
     * @param clientId идентификатор клиента, которому принадлежит заказ.
     */
    public void cancelOrder(String id, Long clientId) {
        UpdateOrderStatusGrpc request = UpdateOrderStatusGrpc.newBuilder()
                .setId(id)
                .setClientId(clientId)
                .setStatus("CANCELED")
                .build();

        ListenableFuture<Empty> grpcFuture = ordersFutureStub.updateOrderStatus(request);
        toCompletableFuture(grpcFuture).join();
    }

}
