package net.rcetech.api.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.rcetech.api.dto.*;
import net.rcetech.api.enums.RequestMethod;
import net.rcetech.api.mapper.DetailsMapper;
import net.rcetech.api.mapper.OrdersMapper;
import net.rcetech.meta.clients.ClientStatus;
import net.rcetech.meta.orders.dto.CreateOrderRequestDTO;
import net.rcetech.meta.orders.dto.GetOrdersFilterDTO;
import net.rcetech.meta.orders.dto.OrdersPageResponseDTO;
import net.rcetech.meta.orders.dto.UpdateOrderStatusRequestDTO;
import net.rcetech.orders.service.OrderApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static net.rcetech.api.constants.Metrics.DETAILS_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderInteractionServiceTest {

    @Mock
    private ApiMerchantDetailsGrpcService detailsGrpcService;

    @Mock
    private DetailsMapper detailsMapper;

    @Mock
    private OrdersMapper ordersMapper;

    @Mock
    private OrderApi orderApi;

    @InjectMocks
    private OrderInteractionService orderInteractionService;

    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Mock
    private Timer timer;

    @Mock
    private Timer.Sample sample;

    private MockedStatic<Timer> timerMock;

    private CreateOrderDTO createOrderDTO;

    private ClientByApiKeyDTO clientDTO;

    private Integer clientOrderTimeout;

    private UUID orderId;

    private ApiDetailsRequestDTO apiDetailsRequestDTO;

    private ApiDetailsResponseDTO detailsResponseDTO;

    private CreateOrderRequestDTO createOrderRequestDTO;

    private net.rcetech.meta.orders.dto.OrderResponseDTO orderResponseDTO;

    private Instant now;

    @BeforeEach
    void setUp() {
        timerMock = mockStatic(Timer.class);

        now = Instant.now();
        orderId = UUID.randomUUID();
        clientOrderTimeout = 300;

        createOrderDTO = CreateOrderDTO.builder()
                .internalId("internal-123")
                .amount(1000)
                .methods(Set.of(RequestMethod.SBP, RequestMethod.CARD))
                .enableUniqueAmount(true)
                .callbackUrl("https://callback.url")
                .userId("user-123")
                .build();

        clientDTO = ClientByApiKeyDTO.builder()
                .clientId(1L)
                .username("testUser")
                .status(ClientStatus.ACTIVE)
                .orderTimeoutSeconds(clientOrderTimeout)
                .build();

        apiDetailsRequestDTO = ApiDetailsRequestDTO.builder()
                .requestId(UUID.randomUUID())
                .internalId(orderId)
                .userId("user-123")
                .amount(1000)
                .methods(Set.of(RequestMethod.SBP, RequestMethod.CARD))
                .build();

        detailsResponseDTO = ApiDetailsResponseDTO.builder()
                .requestId(UUID.randomUUID().toString())
                .orderId("merchant-order-456")
                .orderStatus("PENDING")
                .merchant("Merchant LLC")
                .amount(1000)
                .details(DetailsDTO.builder()
                        .requestMethod("SBP")
                        .details("1234567890")
                        .bank("Test Bank")
                        .operator("Operator")
                        .build())
                .build();

        createOrderRequestDTO = new CreateOrderRequestDTO(
                orderId,
                1L,
                "internal-123",
                "Merchant LLC",
                "merchant-order-456",
                "PENDING",
                1000,
                true,
                "https://callback.url"
        );

        orderResponseDTO = new net.rcetech.meta.orders.dto.OrderResponseDTO(
                orderId,
                1L,
                "internal-123",
                "CREATED",
                1000,
                true,
                "https://callback.url",
                now
        );
    }

    @AfterEach
    void tearDown() {
        if (timerMock != null) {
            timerMock.close();
            timerMock = null;
        }
    }

    @Test
    void shouldCreateOrderSuccessfully() {
        when(detailsMapper.orderToRequestDTO(createOrderDTO)).thenReturn(apiDetailsRequestDTO);
        when(detailsGrpcService.getDetails(apiDetailsRequestDTO, clientDTO)).thenReturn(detailsResponseDTO);
        when(ordersMapper.createRequestDTO(orderId, createOrderDTO, detailsResponseDTO, clientDTO))
                .thenReturn(createOrderRequestDTO);
        when(orderApi.createOrder(createOrderRequestDTO)).thenReturn(orderResponseDTO);

        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(sample);
        doReturn(timer).when(meterRegistry).timer(anyString(), any(String[].class));
        when(sample.stop(any(Timer.class))).thenReturn(100L);

        var result = orderInteractionService.createOrder(createOrderDTO, clientDTO, clientOrderTimeout);

        assertThat(result)
                .isNotNull()
                .satisfies(dto -> {
                    assertThat(dto.getId()).isEqualTo(orderId);
                    assertThat(dto.getInternalId()).isEqualTo("internal-123");
                    assertThat(dto.getStatus()).isEqualTo("CREATED");
                    assertThat(dto.getDetails()).isNotNull();
                    assertThat(dto.getDetails().getRequestMethod()).isEqualTo("SBP");
                    assertThat(dto.getDetails().getDetails()).isEqualTo("1234567890");
                    assertThat(dto.getDetails().getBank()).isEqualTo("Test Bank");
                    assertThat(dto.getCreatedAt()).isEqualTo(now);
                    assertThat(dto.getExpiresAt()).isEqualTo(now.plusSeconds(clientOrderTimeout));
                });

        verify(detailsMapper).orderToRequestDTO(createOrderDTO);
        verify(detailsGrpcService).getDetails(apiDetailsRequestDTO, clientDTO);
        verify(ordersMapper).createRequestDTO(orderId, createOrderDTO, detailsResponseDTO, clientDTO);
        verify(orderApi).createOrder(createOrderRequestDTO);

        verify(meterRegistry, times(1)).timer(
                eq(DETAILS_REQUEST),
                any(String[].class)
        );
    }

    @Test
    void shouldCreateOrderWithEnableUniqueAmountFalse() {
        var createOrderDTOWithoutUnique = CreateOrderDTO.builder()
                .internalId("internal-123")
                .amount(1000)
                .methods(Set.of(RequestMethod.SBP))
                .enableUniqueAmount(false)
                .callbackUrl("https://callback.url")
                .userId("user-123")
                .build();

        var apiDetailsRequestDTOWithoutUnique = ApiDetailsRequestDTO.builder()
                .requestId(UUID.randomUUID())
                .internalId(orderId)
                .userId("user-123")
                .amount(1000)
                .methods(Set.of(RequestMethod.SBP))
                .build();

        var detailsResponseDTOWithoutUnique = ApiDetailsResponseDTO.builder()
                .requestId(UUID.randomUUID().toString())
                .orderId("merchant-order-456")
                .orderStatus("PENDING")
                .merchant("Merchant LLC")
                .amount(null)
                .details(DetailsDTO.builder()
                        .requestMethod("SBP")
                        .details("1234567890")
                        .bank("Test Bank")
                        .build())
                .build();

        var createOrderRequestDTOWithoutUnique = new CreateOrderRequestDTO(
                orderId,
                1L,
                "internal-123",
                "Merchant LLC",
                "merchant-order-456",
                "PENDING",
                1000,
                false,
                "https://callback.url"
        );

        var orderResponseDTOWithoutUnique = new net.rcetech.meta.orders.dto.OrderResponseDTO(
                orderId,
                1L,
                "internal-123",
                "CREATED",
                1000,
                false,
                "https://callback.url",
                now
        );

        when(detailsMapper.orderToRequestDTO(createOrderDTOWithoutUnique)).thenReturn(
                apiDetailsRequestDTOWithoutUnique);
        when(detailsGrpcService.getDetails(apiDetailsRequestDTOWithoutUnique, clientDTO)).thenReturn(
                detailsResponseDTOWithoutUnique);
        when(ordersMapper.createRequestDTO(orderId, createOrderDTOWithoutUnique, detailsResponseDTOWithoutUnique,
                clientDTO))
                .thenReturn(createOrderRequestDTOWithoutUnique);
        when(orderApi.createOrder(createOrderRequestDTOWithoutUnique)).thenReturn(
                orderResponseDTOWithoutUnique);

        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(sample);
        doReturn(timer).when(meterRegistry).timer(anyString(), any(String[].class));
        when(sample.stop(any(Timer.class))).thenReturn(100L);

        var result = orderInteractionService.createOrder(createOrderDTOWithoutUnique, clientDTO, clientOrderTimeout);

        assertThat(result)
                .isNotNull()
                .satisfies(dto -> {
                    assertThat(dto.getId()).isEqualTo(orderId);
                    assertThat(dto.getStatus()).isEqualTo("CREATED");
                    assertThat(dto.getCreatedAt()).isEqualTo(now);
                    assertThat(dto.getExpiresAt()).isEqualTo(now.plusSeconds(clientOrderTimeout));
                });

        verify(detailsMapper).orderToRequestDTO(createOrderDTOWithoutUnique);
        verify(detailsGrpcService).getDetails(apiDetailsRequestDTOWithoutUnique, clientDTO);
        verify(ordersMapper).createRequestDTO(orderId, createOrderDTOWithoutUnique, detailsResponseDTOWithoutUnique,
                clientDTO);
        verify(orderApi).createOrder(createOrderRequestDTOWithoutUnique);
    }

    @Test
    void shouldCreateTestOrder() {
        var result = orderInteractionService.testOrder(createOrderDTO, clientOrderTimeout);

        assertThat(result)
                .isNotNull()
                .satisfies(dto -> {
                    assertThat(dto.getId()).isNotNull();
                    assertThat(dto.getInternalId()).isEqualTo("internal-123");
                    assertThat(dto.getStatus()).isEqualTo("NEW");
                    assertThat(dto.getDetails()).isNotNull();
                    assertThat(dto.getDetails().getRequestMethod()).isEqualTo("CARD");
                    assertThat(dto.getDetails().getDetails()).isEqualTo("1111 2222 3333 4444");
                    assertThat(dto.getDetails().getBank()).isEqualTo("ALFA");
                    assertThat(dto.getCreatedAt()).isNotNull();
                    assertThat(dto.getExpiresAt()).isNotNull();
                    assertThat(dto.getExpiresAt()).isAfter(dto.getCreatedAt());
                });

        verifyNoInteractions(detailsGrpcService);
        verifyNoInteractions(orderApi);
        verifyNoInteractions(detailsMapper);
        verifyNoInteractions(ordersMapper);
    }

    @Test
    void shouldFindOrderById() {
        var id = orderId.toString();

        when(orderApi.getOrders(any(GetOrdersFilterDTO.class)))
                .thenReturn(new OrdersPageResponseDTO(List.of(orderResponseDTO), 1L));

        var result = orderInteractionService.findOrder(id, clientOrderTimeout, clientDTO);

        assertThat(result)
                .isNotNull()
                .satisfies(dto -> {
                    assertThat(dto.getId()).isEqualTo(orderId);
                    assertThat(dto.getInternalId()).isEqualTo("internal-123");
                    assertThat(dto.getStatus()).isEqualTo("CREATED");
                    assertThat(dto.getCreatedAt()).isEqualTo(now);
                    assertThat(dto.getExpiresAt()).isEqualTo(now.plusSeconds(clientOrderTimeout));
                    assertThat(dto.getDetails()).isNull();
                });

        verify(orderApi).getOrders(argThat(filter ->
                filter.id().equals(orderId) &&
                        filter.clientIds().contains(clientDTO.getClientId())
        ));
    }

    @Test
    void shouldFindOrdersWithPagination() {
        var pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());

        var orderId2 = UUID.randomUUID();
        var now2 = Instant.now().plusSeconds(60);

        var orderResponse1 = new net.rcetech.meta.orders.dto.OrderResponseDTO(
                orderId, 1L, "internal-123", "CREATED", 1000, true, null, now);

        var orderResponse2 = new net.rcetech.meta.orders.dto.OrderResponseDTO(
                orderId2, 1L, "internal-456", "PENDING", 500, false, null, now2);

        when(orderApi.getOrders(any(GetOrdersFilterDTO.class)))
                .thenReturn(new OrdersPageResponseDTO(List.of(orderResponse1, orderResponse2), 2L));

        var result = orderInteractionService.findOrders(clientOrderTimeout, clientDTO, pageable);

        assertThat(result)
                .isNotNull()
                .hasSize(2)
                .satisfies(list -> {
                    assertThat(list.getFirst().getId()).isEqualTo(orderId);
                    assertThat(list.getFirst().getInternalId()).isEqualTo("internal-123");
                    assertThat(list.getFirst().getStatus()).isEqualTo("CREATED");
                    assertThat(list.getFirst().getCreatedAt()).isEqualTo(now);
                    assertThat(list.getFirst().getExpiresAt()).isEqualTo(now.plusSeconds(clientOrderTimeout));

                    assertThat(list.get(1).getId()).isEqualTo(orderId2);
                    assertThat(list.get(1).getInternalId()).isEqualTo("internal-456");
                    assertThat(list.get(1).getStatus()).isEqualTo("PENDING");
                    assertThat(list.get(1).getCreatedAt()).isEqualTo(now2);
                    assertThat(list.get(1).getExpiresAt()).isEqualTo(now2.plusSeconds(clientOrderTimeout));
                });

        verify(orderApi).getOrders(argThat(filter ->
                filter.clientIds().contains(clientDTO.getClientId()) &&
                        filter.pagination().page() == 0 &&
                        filter.pagination().size() == 10 &&
                        filter.pagination().sorters().contains("createdAt,desc")
        ));
    }

    @Test
    void shouldCancelOrder() {
        var id = orderId.toString();

        var canceledOrderResponse = new net.rcetech.meta.orders.dto.OrderResponseDTO(
                orderId, 1L, "internal-123", "CANCELED", 1000, true, "https://callback.url", now);

        doNothing().when(orderApi).updateOrderStatus(any(UpdateOrderStatusRequestDTO.class));
        when(orderApi.getOrders(any(GetOrdersFilterDTO.class)))
                .thenReturn(new OrdersPageResponseDTO(List.of(canceledOrderResponse), 1L));

        var result = orderInteractionService.cancelOrder(id, clientOrderTimeout, clientDTO);

        assertThat(result)
                .isNotNull()
                .satisfies(dto -> {
                    assertThat(dto.getId()).isEqualTo(orderId);
                    assertThat(dto.getInternalId()).isEqualTo("internal-123");
                    assertThat(dto.getStatus()).isEqualTo("CANCELED");
                    assertThat(dto.getCreatedAt()).isEqualTo(now);
                    assertThat(dto.getExpiresAt()).isEqualTo(now.plusSeconds(clientOrderTimeout));
                });

        verify(orderApi).updateOrderStatus(
                eq(new UpdateOrderStatusRequestDTO(orderId, "CANCELED", clientDTO.getClientId())));
        verify(orderApi).getOrders(any(GetOrdersFilterDTO.class));
    }

    @Test
    void shouldFindOrdersWithEmptyResult() {
        var pageable = PageRequest.of(0, 10);

        when(orderApi.getOrders(any(GetOrdersFilterDTO.class)))
                .thenReturn(new OrdersPageResponseDTO(List.of(), 0L));

        var result = orderInteractionService.findOrders(clientOrderTimeout, clientDTO, pageable);

        assertThat(result).isEmpty();

        verify(orderApi).getOrders(any(GetOrdersFilterDTO.class));
    }

    @Test
    void shouldHandleNullDetailsInFindOrder() {
        var id = orderId.toString();

        when(orderApi.getOrders(any(GetOrdersFilterDTO.class)))
                .thenReturn(new OrdersPageResponseDTO(List.of(orderResponseDTO), 1L));

        var result = orderInteractionService.findOrder(id, clientOrderTimeout, clientDTO);

        assertThat(result)
                .isNotNull()
                .satisfies(dto -> {
                    assertThat(dto.getId()).isEqualTo(orderId);
                    assertThat(dto.getDetails()).isNull();
                });
    }

    @Test
    void shouldHandleDifferentTimeoutValues() {
        var timeout = 600;

        when(detailsMapper.orderToRequestDTO(createOrderDTO)).thenReturn(apiDetailsRequestDTO);
        when(detailsGrpcService.getDetails(apiDetailsRequestDTO, clientDTO)).thenReturn(detailsResponseDTO);
        when(ordersMapper.createRequestDTO(orderId, createOrderDTO, detailsResponseDTO, clientDTO))
                .thenReturn(createOrderRequestDTO);
        when(orderApi.createOrder(createOrderRequestDTO)).thenReturn(orderResponseDTO);

        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(sample);
        doReturn(timer).when(meterRegistry).timer(anyString(), any(String[].class));
        when(sample.stop(any(Timer.class))).thenReturn(100L);

        var result = orderInteractionService.createOrder(createOrderDTO, clientDTO, timeout);

        assertThat(result)
                .isNotNull()
                .satisfies(dto -> assertThat(dto.getExpiresAt()).isEqualTo(now.plusSeconds(timeout)));
    }

    @Test
    void shouldVerifyOrderFlowSequence() {
        when(detailsMapper.orderToRequestDTO(createOrderDTO)).thenReturn(apiDetailsRequestDTO);
        when(detailsGrpcService.getDetails(apiDetailsRequestDTO, clientDTO)).thenReturn(detailsResponseDTO);
        when(ordersMapper.createRequestDTO(orderId, createOrderDTO, detailsResponseDTO, clientDTO))
                .thenReturn(createOrderRequestDTO);
        when(orderApi.createOrder(createOrderRequestDTO)).thenReturn(orderResponseDTO);

        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(sample);
        doReturn(timer).when(meterRegistry).timer(anyString(), any(String[].class));
        when(sample.stop(any(Timer.class))).thenReturn(100L);

        orderInteractionService.createOrder(createOrderDTO, clientDTO, clientOrderTimeout);

        var inOrder = inOrder(detailsMapper, detailsGrpcService, ordersMapper, orderApi);
        inOrder.verify(detailsMapper).orderToRequestDTO(createOrderDTO);
        inOrder.verify(detailsGrpcService).getDetails(apiDetailsRequestDTO, clientDTO);
        inOrder.verify(ordersMapper).createRequestDTO(orderId, createOrderDTO, detailsResponseDTO, clientDTO);
        inOrder.verify(orderApi).createOrder(createOrderRequestDTO);
    }

    @Test
    void shouldHandleCreateOrderWithNullCallbackUrl() {
        var createOrderWithoutCallback = CreateOrderDTO.builder()
                .internalId("internal-123")
                .amount(1000)
                .methods(Set.of(RequestMethod.SBP))
                .enableUniqueAmount(true)
                .callbackUrl(null)
                .userId("user-123")
                .build();

        var createOrderRequestWithoutCallback = new CreateOrderRequestDTO(
                orderId,
                1L,
                "internal-123",
                "Merchant LLC",
                "merchant-order-456",
                "PENDING",
                1000,
                true,
                null
        );

        when(detailsMapper.orderToRequestDTO(createOrderWithoutCallback)).thenReturn(apiDetailsRequestDTO);
        when(detailsGrpcService.getDetails(apiDetailsRequestDTO, clientDTO)).thenReturn(detailsResponseDTO);
        when(ordersMapper.createRequestDTO(orderId, createOrderWithoutCallback, detailsResponseDTO, clientDTO))
                .thenReturn(createOrderRequestWithoutCallback);
        when(orderApi.createOrder(createOrderRequestWithoutCallback)).thenReturn(orderResponseDTO);

        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(sample);
        doReturn(timer).when(meterRegistry).timer(anyString(), any(String[].class));
        when(sample.stop(any(Timer.class))).thenReturn(100L);

        var result = orderInteractionService.createOrder(createOrderWithoutCallback, clientDTO, clientOrderTimeout);

        assertThat(result).isNotNull();
        assertThat(result.getInternalId()).isEqualTo("internal-123");

        verify(ordersMapper).createRequestDTO(orderId, createOrderWithoutCallback, detailsResponseDTO, clientDTO);
        verify(orderApi).createOrder(createOrderRequestWithoutCallback);
    }

}