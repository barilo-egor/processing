package net.rcetech.processingdetailsapi.service.unit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.rcetech.processingdetailsapi.dto.*;
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
import tgb.cryptoexchange.processingdetailsapi.dto.*;
import net.rcetech.processingdetailsapi.enums.ClientStatus;
import net.rcetech.processingdetailsapi.enums.RequestMethod;
import net.rcetech.processingdetailsapi.mapper.DetailsMapper;
import net.rcetech.processingdetailsapi.mapper.OrdersMapper;
import net.rcetech.processingdetailsapi.service.ApiMerchantDetailsGrpcService;
import net.rcetech.processingdetailsapi.service.ApiOrdersGrpcService;
import net.rcetech.processingdetailsapi.service.OrderService;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static net.rcetech.processingdetailsapi.constants.Metrics.DETAILS_REQUEST;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private ApiMerchantDetailsGrpcService detailsGrpcService;

    @Mock
    private DetailsMapper detailsMapper;

    @Mock
    private OrdersMapper ordersMapper;

    @Mock
    private ApiOrdersGrpcService apiOrdersGrpcService;

    @InjectMocks
    private OrderService orderService;

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

    private ApiOrdersCreateRequestDTO ordersCreateRequestDTO;

    private ApiOrdersResponseDTO ordersResponseDTO;

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

        ordersCreateRequestDTO = ApiOrdersCreateRequestDTO.builder()
                .id(orderId)
                .clientId(1L)
                .internalId("internal-123")
                .merchant("Merchant LLC")
                .merchantOrderId("merchant-order-456")
                .merchantOrderStatus("PENDING")
                .amount(1000)
                .enableUniqueAmount(true)
                .callbackUrl("https://callback.url")
                .build();

        ordersResponseDTO = ApiOrdersResponseDTO.builder()
                .id(orderId)
                .clientId(1L)
                .internalId("internal-123")
                .status("CREATED")
                .amount(1000)
                .enableUniqueAmount(true)
                .callbackUrl("https://callback.url")
                .createdAt(now)
                .build();
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
                .thenReturn(ordersCreateRequestDTO);
        when(apiOrdersGrpcService.createOrder(ordersCreateRequestDTO)).thenReturn(ordersResponseDTO);

        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(sample);
        doReturn(timer).when(meterRegistry).timer(anyString(), any(String[].class));
        when(sample.stop(any(Timer.class))).thenReturn(100L);

        var result = orderService.createOrder(createOrderDTO, clientDTO, clientOrderTimeout);

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
        verify(apiOrdersGrpcService).createOrder(ordersCreateRequestDTO);

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

        var ordersCreateRequestDTOWithoutUnique = ApiOrdersCreateRequestDTO.builder()
                .id(orderId)
                .clientId(1L)
                .internalId("internal-123")
                .merchant("Merchant LLC")
                .merchantOrderId("merchant-order-456")
                .merchantOrderStatus("PENDING")
                .amount(1000)
                .enableUniqueAmount(false)
                .callbackUrl("https://callback.url")
                .build();

        var ordersResponseDTOWithoutUnique = ApiOrdersResponseDTO.builder()
                .id(orderId)
                .clientId(1L)
                .internalId("internal-123")
                .status("CREATED")
                .amount(1000)
                .enableUniqueAmount(false)
                .callbackUrl("https://callback.url")
                .createdAt(now)
                .build();

        when(detailsMapper.orderToRequestDTO(createOrderDTOWithoutUnique)).thenReturn(
                apiDetailsRequestDTOWithoutUnique);
        when(detailsGrpcService.getDetails(apiDetailsRequestDTOWithoutUnique, clientDTO)).thenReturn(
                detailsResponseDTOWithoutUnique);
        when(ordersMapper.createRequestDTO(orderId, createOrderDTOWithoutUnique, detailsResponseDTOWithoutUnique,
                clientDTO))
                .thenReturn(ordersCreateRequestDTOWithoutUnique);
        when(apiOrdersGrpcService.createOrder(ordersCreateRequestDTOWithoutUnique)).thenReturn(
                ordersResponseDTOWithoutUnique);

        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(sample);
        doReturn(timer).when(meterRegistry).timer(anyString(), any(String[].class));
        when(sample.stop(any(Timer.class))).thenReturn(100L);

        var result = orderService.createOrder(createOrderDTOWithoutUnique, clientDTO, clientOrderTimeout);

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
        verify(apiOrdersGrpcService).createOrder(ordersCreateRequestDTOWithoutUnique);
    }

    @Test
    void shouldCreateTestOrder() {
        var result = orderService.testOrder(createOrderDTO, clientOrderTimeout);

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
        verifyNoInteractions(apiOrdersGrpcService);
        verifyNoInteractions(detailsMapper);
        verifyNoInteractions(ordersMapper);
    }

    @Test
    void shouldFindOrderById() {
        var id = "order-123";

        when(apiOrdersGrpcService.getOrders(id, clientDTO.getClientId())).thenReturn(ordersResponseDTO);

        var result = orderService.findOrder(id, clientOrderTimeout, clientDTO);

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

        verify(apiOrdersGrpcService).getOrders(id, clientDTO.getClientId());
    }

    @Test
    void shouldFindOrdersWithPagination() {
        var pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());

        var orderId2 = UUID.randomUUID();
        var now2 = Instant.now().plusSeconds(60);

        var orderResponse1 = ApiOrdersResponseDTO.builder()
                .id(orderId)
                .clientId(1L)
                .internalId("internal-123")
                .status("CREATED")
                .amount(1000)
                .enableUniqueAmount(true)
                .createdAt(now)
                .build();

        var orderResponse2 = ApiOrdersResponseDTO.builder()
                .id(orderId2)
                .clientId(1L)
                .internalId("internal-456")
                .status("PENDING")
                .amount(500)
                .enableUniqueAmount(false)
                .createdAt(now2)
                .build();

        var orderList = List.of(orderResponse1, orderResponse2);

        when(apiOrdersGrpcService.findOrders(clientDTO.getClientId(), pageable)).thenReturn(orderList);

        var result = orderService.findOrders(clientOrderTimeout, clientDTO, pageable);

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

        verify(apiOrdersGrpcService).findOrders(clientDTO.getClientId(), pageable);
    }

    @Test
    void shouldCancelOrder() {
        var id = "order-123";

        var canceledOrderResponse = ApiOrdersResponseDTO.builder()
                .id(orderId)
                .clientId(1L)
                .internalId("internal-123")
                .status("CANCELED")
                .amount(1000)
                .enableUniqueAmount(true)
                .createdAt(now)
                .build();

        when(apiOrdersGrpcService.getOrders(id, clientDTO.getClientId())).thenReturn(canceledOrderResponse);

        var result = orderService.cancelOrder(id, clientOrderTimeout, clientDTO);

        assertThat(result)
                .isNotNull()
                .satisfies(dto -> {
                    assertThat(dto.getId()).isEqualTo(orderId);
                    assertThat(dto.getInternalId()).isEqualTo("internal-123");
                    assertThat(dto.getStatus()).isEqualTo("CANCELED");
                    assertThat(dto.getCreatedAt()).isEqualTo(now);
                    assertThat(dto.getExpiresAt()).isEqualTo(now.plusSeconds(clientOrderTimeout));
                });

        verify(apiOrdersGrpcService).cancelOrder(id, clientDTO.getClientId());
        verify(apiOrdersGrpcService).getOrders(id, clientDTO.getClientId());
    }

    @Test
    void shouldFindOrdersWithEmptyResult() {
        var pageable = PageRequest.of(0, 10);

        when(apiOrdersGrpcService.findOrders(clientDTO.getClientId(), pageable)).thenReturn(List.of());

        var result = orderService.findOrders(clientOrderTimeout, clientDTO, pageable);

        assertThat(result).isEmpty();

        verify(apiOrdersGrpcService).findOrders(clientDTO.getClientId(), pageable);
    }

    @Test
    void shouldHandleNullDetailsInFindOrder() {
        var id = "order-123";
        var ordersResponseWithoutDetails = ApiOrdersResponseDTO.builder()
                .id(orderId)
                .clientId(1L)
                .internalId("internal-123")
                .status("CREATED")
                .amount(1000)
                .enableUniqueAmount(true)
                .createdAt(now)
                .build();

        when(apiOrdersGrpcService.getOrders(id, clientDTO.getClientId())).thenReturn(ordersResponseWithoutDetails);

        var result = orderService.findOrder(id, clientOrderTimeout, clientDTO);

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
                .thenReturn(ordersCreateRequestDTO);
        when(apiOrdersGrpcService.createOrder(ordersCreateRequestDTO)).thenReturn(ordersResponseDTO);

        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(sample);
        doReturn(timer).when(meterRegistry).timer(anyString(), any(String[].class));
        when(sample.stop(any(Timer.class))).thenReturn(100L);

        var result = orderService.createOrder(createOrderDTO, clientDTO, timeout);

        assertThat(result)
                .isNotNull()
                .satisfies(dto -> assertThat(dto.getExpiresAt()).isEqualTo(now.plusSeconds(timeout)));
    }

    @Test
    void shouldVerifyOrderFlowSequence() {
        when(detailsMapper.orderToRequestDTO(createOrderDTO)).thenReturn(apiDetailsRequestDTO);
        when(detailsGrpcService.getDetails(apiDetailsRequestDTO, clientDTO)).thenReturn(detailsResponseDTO);
        when(ordersMapper.createRequestDTO(orderId, createOrderDTO, detailsResponseDTO, clientDTO))
                .thenReturn(ordersCreateRequestDTO);
        when(apiOrdersGrpcService.createOrder(ordersCreateRequestDTO)).thenReturn(ordersResponseDTO);

        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(sample);
        doReturn(timer).when(meterRegistry).timer(anyString(), any(String[].class));
        when(sample.stop(any(Timer.class))).thenReturn(100L);

        orderService.createOrder(createOrderDTO, clientDTO, clientOrderTimeout);

        var inOrder = inOrder(detailsMapper, detailsGrpcService, ordersMapper, apiOrdersGrpcService);
        inOrder.verify(detailsMapper).orderToRequestDTO(createOrderDTO);
        inOrder.verify(detailsGrpcService).getDetails(apiDetailsRequestDTO, clientDTO);
        inOrder.verify(ordersMapper).createRequestDTO(orderId, createOrderDTO, detailsResponseDTO, clientDTO);
        inOrder.verify(apiOrdersGrpcService).createOrder(ordersCreateRequestDTO);
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

        var ordersCreateWithoutCallback = ApiOrdersCreateRequestDTO.builder()
                .id(orderId)
                .clientId(1L)
                .internalId("internal-123")
                .merchant("Merchant LLC")
                .merchantOrderId("merchant-order-456")
                .merchantOrderStatus("PENDING")
                .amount(1000)
                .enableUniqueAmount(true)
                .callbackUrl(null)
                .build();

        when(detailsMapper.orderToRequestDTO(createOrderWithoutCallback)).thenReturn(apiDetailsRequestDTO);
        when(detailsGrpcService.getDetails(apiDetailsRequestDTO, clientDTO)).thenReturn(detailsResponseDTO);
        when(ordersMapper.createRequestDTO(orderId, createOrderWithoutCallback, detailsResponseDTO, clientDTO))
                .thenReturn(ordersCreateWithoutCallback);
        when(apiOrdersGrpcService.createOrder(ordersCreateWithoutCallback)).thenReturn(ordersResponseDTO);
        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(sample);
        doReturn(timer).when(meterRegistry).timer(anyString(), any(String[].class));
        when(sample.stop(any(Timer.class))).thenReturn(100L);

        var result = orderService.createOrder(createOrderWithoutCallback, clientDTO, clientOrderTimeout);

        assertThat(result).isNotNull();
        assertThat(result.getInternalId()).isEqualTo("internal-123");

        verify(ordersMapper).createRequestDTO(orderId, createOrderWithoutCallback, detailsResponseDTO, clientDTO);
        verify(apiOrdersGrpcService).createOrder(ordersCreateWithoutCallback);
    }

}