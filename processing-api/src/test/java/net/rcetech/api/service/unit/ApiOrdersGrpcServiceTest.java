package net.rcetech.api.service.unit;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import net.rcetech.grpc.generated.*;
import net.rcetech.api.dto.ApiOrdersCreateRequestDTO;
import net.rcetech.api.dto.ApiOrdersResponseDTO;
import net.rcetech.api.exceptions.OrderNotFoundException;
import net.rcetech.api.mapper.OrdersMapper;
import net.rcetech.api.service.ApiOrdersGrpcService;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiOrdersGrpcServiceTest {

    @Mock
    private OrdersServiceGrpc.OrdersServiceFutureStub ordersFutureStub;

    @Mock
    private OrdersMapper ordersMapper;

    @Mock
    private ListenableFuture<CreateOrderResponseGrpc> createOrderFuture;

    @Mock
    private ListenableFuture<GetOrdersResponseGrpc> getOrdersFuture;

    @Mock
    private ListenableFuture<Empty> emptyFuture;

    @InjectMocks
    private ApiOrdersGrpcService service;

    private UUID orderId;

    private Long clientId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        clientId = 123L;
    }

    @Test
    void shouldCreateOrderSuccessfully() throws Exception {
        var createRequestDTO = ApiOrdersCreateRequestDTO.builder()
                .id(orderId)
                .clientId(clientId)
                .internalId("internal-456")
                .merchant("Merchant LLC")
                .merchantOrderId("order-789")
                .merchantOrderStatus("PENDING")
                .amount(1000)
                .enableUniqueAmount(true)
                .callbackUrl("https://callback.url")
                .build();

        var grpcRequest = CreateOrderGrpc.newBuilder()
                .setId(orderId.toString())
                .setClientId(clientId)
                .setInternalId("internal-456")
                .setMerchant("Merchant LLC")
                .setMerchantOrderId("order-789")
                .setMerchantOrderStatus("PENDING")
                .setAmount(1000)
                .setEnableUniqueAmount(true)
                .setCallbackUrl("https://callback.url")
                .build();

        var grpcResponse = CreateOrderResponseGrpc.newBuilder()
                .setId(orderId.toString())
                .setClientId(clientId)
                .setInternalId("internal-456")
                .setStatus("CREATED")
                .setAmount(1000)
                .setEnableUniqueAmount(true)
                .setCallbackUrl("https://callback.url")
                .setCreatedAt(Timestamp.newBuilder()
                        .setSeconds(Instant.now().getEpochSecond())
                        .build())
                .build();

        var expectedResponse = ApiOrdersResponseDTO.builder()
                .id(orderId)
                .clientId(clientId)
                .internalId("internal-456")
                .status("CREATED")
                .amount(1000)
                .enableUniqueAmount(true)
                .callbackUrl("https://callback.url")
                .createdAt(Instant.now())
                .build();

        when(ordersMapper.createOrderGrpc(createRequestDTO)).thenReturn(grpcRequest);
        when(ordersFutureStub.createOrder(grpcRequest)).thenReturn(createOrderFuture);
        when(createOrderFuture.get()).thenReturn(grpcResponse);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(createOrderFuture).addListener(any(Runnable.class), any());
        when(ordersMapper.grpcResponseToDTO(grpcResponse)).thenReturn(expectedResponse);

        var result = service.createOrder(createRequestDTO);

        assertThat(result)
                .isNotNull()
                .satisfies(dto -> {
                    assertThat(dto.getId()).isEqualTo(orderId);
                    assertThat(dto.getClientId()).isEqualTo(clientId);
                    assertThat(dto.getInternalId()).isEqualTo("internal-456");
                    assertThat(dto.getStatus()).isEqualTo("CREATED");
                    assertThat(dto.getAmount()).isEqualTo(1000);
                    assertThat(dto.isEnableUniqueAmount()).isTrue();
                    assertThat(dto.getCallbackUrl()).isEqualTo("https://callback.url");
                });

        verify(ordersMapper).createOrderGrpc(createRequestDTO);
        verify(ordersFutureStub).createOrder(grpcRequest);
        verify(createOrderFuture).get();
        verify(createOrderFuture).addListener(any(Runnable.class), any());
        verify(ordersMapper).grpcResponseToDTO(grpcResponse);
    }

    @Test
    void shouldCreateOrderWithoutCallbackUrl() throws Exception {
        var createRequestDTO = ApiOrdersCreateRequestDTO.builder()
                .id(orderId)
                .clientId(clientId)
                .internalId("internal-456")
                .merchant("Merchant LLC")
                .merchantOrderId("order-789")
                .merchantOrderStatus("PENDING")
                .amount(1000)
                .enableUniqueAmount(false)
                .callbackUrl(null)
                .build();

        var grpcRequest = CreateOrderGrpc.newBuilder()
                .setId(orderId.toString())
                .setClientId(clientId)
                .setInternalId("internal-456")
                .setMerchant("Merchant LLC")
                .setMerchantOrderId("order-789")
                .setMerchantOrderStatus("PENDING")
                .setAmount(1000)
                .setEnableUniqueAmount(false)
                .build();

        var grpcResponse = CreateOrderResponseGrpc.newBuilder()
                .setId(orderId.toString())
                .setClientId(clientId)
                .setInternalId("internal-456")
                .setStatus("CREATED")
                .setAmount(1000)
                .setEnableUniqueAmount(false)
                .setCreatedAt(Timestamp.newBuilder()
                        .setSeconds(Instant.now().getEpochSecond())
                        .build())
                .build();

        var expectedResponse = ApiOrdersResponseDTO.builder()
                .id(orderId)
                .clientId(clientId)
                .internalId("internal-456")
                .status("CREATED")
                .amount(1000)
                .enableUniqueAmount(false)
                .createdAt(Instant.now())
                .build();

        when(ordersMapper.createOrderGrpc(createRequestDTO)).thenReturn(grpcRequest);
        when(ordersFutureStub.createOrder(grpcRequest)).thenReturn(createOrderFuture);
        when(createOrderFuture.get()).thenReturn(grpcResponse);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(createOrderFuture).addListener(any(Runnable.class), any());
        when(ordersMapper.grpcResponseToDTO(grpcResponse)).thenReturn(expectedResponse);

        var result = service.createOrder(createRequestDTO);

        assertThat(result).isNotNull();
        assertThat(result.getCallbackUrl()).isNull();

        verify(ordersMapper).createOrderGrpc(createRequestDTO);
    }

    @Test
    void shouldGetOrderByIdSuccessfully() throws Exception {
        var id = orderId.toString();

        var grpcRequest = GetOrdersGrpc.newBuilder()
                .setId(id)
                .addClientIds(clientId)
                .setPagination(PaginationParams.newBuilder()
                        .setPage(0)
                        .setSize(1)
                        .build())
                .build();

        var orderResponse = OrderResponse.newBuilder()
                .setId(id)
                .setClientId(clientId)
                .setInternalId("internal-456")
                .setStatus("ACTIVE")
                .setAmount(1000)
                .setEnableUniqueAmount(true)
                .setCreatedAt(Timestamp.newBuilder()
                        .setSeconds(Instant.now().getEpochSecond())
                        .build())
                .build();

        var grpcResponse = GetOrdersResponseGrpc.newBuilder()
                .addOrders(orderResponse)
                .setTotalElements(1)
                .build();

        var expectedResponse = ApiOrdersResponseDTO.builder()
                .id(orderId)
                .clientId(clientId)
                .internalId("internal-456")
                .status("ACTIVE")
                .amount(1000)
                .enableUniqueAmount(true)
                .createdAt(Instant.now())
                .build();

        when(ordersMapper.getOrdersByIdGrpc(id, clientId)).thenReturn(grpcRequest);
        when(ordersFutureStub.getOrders(grpcRequest)).thenReturn(getOrdersFuture);
        when(getOrdersFuture.get()).thenReturn(grpcResponse);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(getOrdersFuture).addListener(any(Runnable.class), any());
        when(ordersMapper.getOrder(orderResponse)).thenReturn(expectedResponse);

        var result = service.getOrders(id, clientId);

        assertThat(result)
                .isNotNull()
                .satisfies(dto -> {
                    assertThat(dto.getId()).isEqualTo(orderId);
                    assertThat(dto.getClientId()).isEqualTo(clientId);
                    assertThat(dto.getStatus()).isEqualTo("ACTIVE");
                });

        verify(ordersMapper).getOrdersByIdGrpc(id, clientId);
        verify(ordersFutureStub).getOrders(grpcRequest);
        verify(getOrdersFuture).get();
        verify(getOrdersFuture).addListener(any(Runnable.class), any());
        verify(ordersMapper).getOrder(orderResponse);
    }

    @Test
    void shouldGetOrderByExternalIdWhenNotFoundById() throws Exception {
        var externalId = "external-456";

        var byIdRequest = GetOrdersGrpc.newBuilder()
                .setId(externalId)
                .addClientIds(clientId)
                .setPagination(PaginationParams.newBuilder()
                        .setPage(0)
                        .setSize(1)
                        .build())
                .build();

        var byExternalIdRequest = GetOrdersGrpc.newBuilder()
                .setInternalId(externalId)
                .addClientIds(clientId)
                .setPagination(PaginationParams.newBuilder()
                        .setPage(0)
                        .setSize(1)
                        .build())
                .build();

        var emptyResponse = GetOrdersResponseGrpc.newBuilder()
                .setTotalElements(0)
                .build();

        var orderResponse = OrderResponse.newBuilder()
                .setId(orderId.toString())
                .setClientId(clientId)
                .setInternalId(externalId)
                .setStatus("ACTIVE")
                .setAmount(1000)
                .setEnableUniqueAmount(true)
                .setCreatedAt(Timestamp.newBuilder()
                        .setSeconds(Instant.now().getEpochSecond())
                        .build())
                .build();

        var foundResponse = GetOrdersResponseGrpc.newBuilder()
                .addOrders(orderResponse)
                .setTotalElements(1)
                .build();

        var expectedResponse = ApiOrdersResponseDTO.builder()
                .id(orderId)
                .clientId(clientId)
                .internalId(externalId)
                .status("ACTIVE")
                .amount(1000)
                .enableUniqueAmount(true)
                .createdAt(Instant.now())
                .build();

        ListenableFuture<GetOrdersResponseGrpc> listenableFuture1 = Mockito.mock();
        ListenableFuture<GetOrdersResponseGrpc> listenableFuture2 = Mockito.mock();

        when(ordersMapper.getOrdersByIdGrpc(externalId, clientId)).thenReturn(byIdRequest);
        when(ordersFutureStub.getOrders(byIdRequest)).thenReturn(listenableFuture1);
        when(listenableFuture1.get()).thenReturn(emptyResponse);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(listenableFuture1).addListener(any(Runnable.class), any());

        when(ordersMapper.getOrdersByExternalIdGrpc(externalId, clientId)).thenReturn(byExternalIdRequest);
        when(ordersFutureStub.getOrders(byExternalIdRequest)).thenReturn(listenableFuture2);
        when(listenableFuture2.get()).thenReturn(foundResponse);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(listenableFuture2).addListener(any(Runnable.class), any());

        when(ordersMapper.getOrder(orderResponse)).thenReturn(expectedResponse);

        var result = service.getOrders(externalId, clientId);

        assertThat(result)
                .isNotNull()
                .satisfies(dto -> {
                    assertThat(dto.getInternalId()).isEqualTo(externalId);
                    assertThat(dto.getId()).isEqualTo(orderId);
                });

        verify(ordersMapper).getOrdersByIdGrpc(externalId, clientId);
        verify(ordersMapper).getOrdersByExternalIdGrpc(externalId, clientId);
        verify(ordersFutureStub, times(1)).getOrders(byIdRequest);
        verify(ordersFutureStub, times(1)).getOrders(byExternalIdRequest);
        verify(ordersMapper).getOrder(orderResponse);
    }

    @Test
    void shouldThrowOrderNotFoundExceptionWhenOrderNotFound() throws Exception {
        var id = "non-existent";

        var byIdRequest = GetOrdersGrpc.newBuilder()
                .setId(id)
                .addClientIds(clientId)
                .setPagination(PaginationParams.newBuilder()
                        .setPage(0)
                        .setSize(1)
                        .build())
                .build();

        var byExternalIdRequest = GetOrdersGrpc.newBuilder()
                .setInternalId(id)
                .addClientIds(clientId)
                .setPagination(PaginationParams.newBuilder()
                        .setPage(0)
                        .setSize(1)
                        .build())
                .build();

        var emptyResponse = GetOrdersResponseGrpc.newBuilder()
                .setTotalElements(0)
                .build();

        ListenableFuture<GetOrdersResponseGrpc> listenableFuture1 = mock();
        ListenableFuture<GetOrdersResponseGrpc> listenableFuture2 = mock();

        when(ordersMapper.getOrdersByIdGrpc(id, clientId)).thenReturn(byIdRequest);
        when(ordersFutureStub.getOrders(byIdRequest)).thenReturn(listenableFuture1);
        when(listenableFuture1.get()).thenReturn(emptyResponse);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(listenableFuture1).addListener(any(Runnable.class), any());

        when(ordersMapper.getOrdersByExternalIdGrpc(id, clientId)).thenReturn(byExternalIdRequest);
        when(ordersFutureStub.getOrders(byExternalIdRequest)).thenReturn(listenableFuture2);
        when(listenableFuture2.get()).thenReturn(emptyResponse);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(listenableFuture2).addListener(any(Runnable.class), any());

        assertThatThrownBy(() -> service.getOrders(id, clientId))
                .isInstanceOf(OrderNotFoundException.class);

        verify(ordersMapper).getOrdersByIdGrpc(id, clientId);
        verify(ordersMapper).getOrdersByExternalIdGrpc(id, clientId);
        verify(ordersFutureStub, times(2)).getOrders(any(GetOrdersGrpc.class));
        verify(ordersMapper, never()).getOrder(any());
    }

    @Test
    void shouldFindOrdersWithPagination() throws Exception {
        var pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());

        var orderId1 = UUID.randomUUID();
        var orderId2 = UUID.randomUUID();

        var grpcRequest = GetOrdersGrpc.newBuilder()
                .addClientIds(clientId)
                .setPagination(PaginationParams.newBuilder()
                        .setPage(0)
                        .setSize(10)
                        .addSorters("createdAt,desc")
                        .build())
                .build();

        var order1 = OrderResponse.newBuilder()
                .setId(orderId1.toString())
                .setClientId(clientId)
                .setInternalId("internal-1")
                .setStatus("ACTIVE")
                .setAmount(1000)
                .setEnableUniqueAmount(true)
                .setCreatedAt(Timestamp.newBuilder()
                        .setSeconds(Instant.now().getEpochSecond())
                        .build())
                .build();

        var order2 = OrderResponse.newBuilder()
                .setId(orderId2.toString())
                .setClientId(clientId)
                .setInternalId("internal-2")
                .setStatus("PENDING")
                .setAmount(500)
                .setEnableUniqueAmount(false)
                .setCreatedAt(Timestamp.newBuilder()
                        .setSeconds(Instant.now().getEpochSecond())
                        .build())
                .build();

        var grpcResponse = GetOrdersResponseGrpc.newBuilder()
                .addOrders(order1)
                .addOrders(order2)
                .setTotalElements(2)
                .build();

        var expectedResponse1 = ApiOrdersResponseDTO.builder()
                .id(orderId1)
                .clientId(clientId)
                .internalId("internal-1")
                .status("ACTIVE")
                .amount(1000)
                .enableUniqueAmount(true)
                .createdAt(Instant.now())
                .build();

        var expectedResponse2 = ApiOrdersResponseDTO.builder()
                .id(orderId2)
                .clientId(clientId)
                .internalId("internal-2")
                .status("PENDING")
                .amount(500)
                .enableUniqueAmount(false)
                .createdAt(Instant.now())
                .build();

        when(ordersMapper.getOrdersGrpc(clientId, pageable)).thenReturn(grpcRequest);
        when(ordersFutureStub.getOrders(grpcRequest)).thenReturn(getOrdersFuture);
        when(getOrdersFuture.get()).thenReturn(grpcResponse);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(getOrdersFuture).addListener(any(Runnable.class), any());
        when(ordersMapper.getOrder(order1)).thenReturn(expectedResponse1);
        when(ordersMapper.getOrder(order2)).thenReturn(expectedResponse2);

        var result = service.findOrders(clientId, pageable);

        assertThat(result)
                .isNotNull()
                .hasSize(2)
                .satisfies(list -> {
                    assertThat(list.get(0).getId()).isEqualTo(orderId1);
                    assertThat(list.get(0).getStatus()).isEqualTo("ACTIVE");
                    assertThat(list.get(1).getId()).isEqualTo(orderId2);
                    assertThat(list.get(1).getStatus()).isEqualTo("PENDING");
                });

        verify(ordersMapper).getOrdersGrpc(clientId, pageable);
        verify(ordersFutureStub).getOrders(grpcRequest);
        verify(getOrdersFuture).get();
        verify(getOrdersFuture).addListener(any(Runnable.class), any());
        verify(ordersMapper, times(2)).getOrder(any(OrderResponse.class));
    }

    @Test
    void shouldFindOrdersWithSorting() throws Exception {
        var pageable = PageRequest.of(1, 5, Sort.by("amount").ascending().and(Sort.by("status").descending()));

        var grpcRequest = GetOrdersGrpc.newBuilder()
                .addClientIds(clientId)
                .setPagination(PaginationParams.newBuilder()
                        .setPage(1)
                        .setSize(5)
                        .addSorters("amount,asc")
                        .addSorters("status,desc")
                        .build())
                .build();

        var grpcResponse = GetOrdersResponseGrpc.newBuilder()
                .setTotalElements(0)
                .build();

        when(ordersMapper.getOrdersGrpc(clientId, pageable)).thenReturn(grpcRequest);
        when(ordersFutureStub.getOrders(grpcRequest)).thenReturn(getOrdersFuture);
        when(getOrdersFuture.get()).thenReturn(grpcResponse);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(getOrdersFuture).addListener(any(Runnable.class), any());

        var result = service.findOrders(clientId, pageable);

        assertThat(result).isEmpty();

        var captor = ArgumentCaptor.forClass(GetOrdersGrpc.class);
        verify(ordersFutureStub).getOrders(captor.capture());
        var capturedRequest = captor.getValue();

        assertThat(capturedRequest.getPagination().getPage()).isEqualTo(1);
        assertThat(capturedRequest.getPagination().getSize()).isEqualTo(5);
        assertThat(capturedRequest.getPagination().getSortersList())
                .containsExactly("amount,asc", "status,desc");
    }

    @Test
    void shouldCancelOrderSuccessfully() throws Exception {
        var id = "order-123";

        var expectedRequest = UpdateOrderStatusGrpc.newBuilder()
                .setId(id)
                .setClientId(clientId)
                .setStatus("CANCELED")
                .build();

        when(ordersFutureStub.updateOrderStatus(expectedRequest)).thenReturn(emptyFuture);
        when(emptyFuture.get()).thenReturn(Empty.getDefaultInstance());
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(emptyFuture).addListener(any(Runnable.class), any());

        service.cancelOrder(id, clientId);

        var captor = ArgumentCaptor.forClass(UpdateOrderStatusGrpc.class);
        verify(ordersFutureStub).updateOrderStatus(captor.capture());
        var capturedRequest = captor.getValue();

        assertThat(capturedRequest.getId()).isEqualTo(id);
        assertThat(capturedRequest.getClientId()).isEqualTo(clientId);
        assertThat(capturedRequest.getStatus()).isEqualTo("CANCELED");

        verify(emptyFuture).get();
        verify(emptyFuture).addListener(any(Runnable.class), any());
    }

    @Test
    void shouldCancelOrderWithoutClientId() {
        var id = "order-123";
        Long nullClientId = null;

        assertThatThrownBy(() -> service.cancelOrder(id, nullClientId))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldHandleFindOrdersWithEmptyResult() throws Exception {
        var pageable = PageRequest.of(0, 10);

        var grpcRequest = GetOrdersGrpc.newBuilder()
                .addClientIds(clientId)
                .setPagination(PaginationParams.newBuilder()
                        .setPage(0)
                        .setSize(10)
                        .build())
                .build();

        var grpcResponse = GetOrdersResponseGrpc.newBuilder()
                .setTotalElements(0)
                .build();

        when(ordersMapper.getOrdersGrpc(clientId, pageable)).thenReturn(grpcRequest);
        when(ordersFutureStub.getOrders(grpcRequest)).thenReturn(getOrdersFuture);
        when(getOrdersFuture.get()).thenReturn(grpcResponse);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(getOrdersFuture).addListener(any(Runnable.class), any());

        var result = service.findOrders(clientId, pageable);

        assertThat(result).isEmpty();
        verify(ordersMapper, never()).getOrder(any());
    }

    @Test
    void shouldFindOrdersWithDefaultPagination() throws Exception {
        var pageable = PageRequest.of(0, 20);

        var grpcRequest = GetOrdersGrpc.newBuilder()
                .addClientIds(clientId)
                .setPagination(PaginationParams.newBuilder()
                        .setPage(0)
                        .setSize(20)
                        .build())
                .build();

        var grpcResponse = GetOrdersResponseGrpc.newBuilder()
                .setTotalElements(0)
                .build();

        when(ordersMapper.getOrdersGrpc(clientId, pageable)).thenReturn(grpcRequest);
        when(ordersFutureStub.getOrders(grpcRequest)).thenReturn(getOrdersFuture);
        when(getOrdersFuture.get()).thenReturn(grpcResponse);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(getOrdersFuture).addListener(any(Runnable.class), any());

        var result = service.findOrders(clientId, pageable);

        assertThat(result).isEmpty();

        var captor = ArgumentCaptor.forClass(GetOrdersGrpc.class);
        verify(ordersFutureStub).getOrders(captor.capture());
        assertThat(captor.getValue().getPagination().getPage()).isEqualTo(0);
        assertThat(captor.getValue().getPagination().getSize()).isEqualTo(20);
    }

}
