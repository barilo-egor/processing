package net.rcetech.api.service.integration;

import com.google.common.util.concurrent.Futures;
import net.rcetech.api.dto.ClientByApiKeyDTO;
import net.rcetech.api.dto.CreateOrderDTO;
import net.rcetech.api.enums.ClientStatus;
import net.rcetech.grpc.generated.DetailsGrpc;
import net.rcetech.grpc.generated.GetDetailsResponseGrpc;
import net.rcetech.meta.orders.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OrderControllerIT extends BaseIntegrationTest {

    private static final String API_KEY = "professional-test-token";

    private static final String CLIENT_SECRET = "super-secret-key-for-hmac-verification-12345";

    @BeforeEach
    void setUpAuth() {
        ClientByApiKeyDTO mockClient = ClientByApiKeyDTO.builder()
                .clientId(99L)
                .username("Professional Merchant")
                .apiKeyPreview("prof...")
                .secret(CLIENT_SECRET)
                .status(ClientStatus.ACTIVE)
                .orderTimeoutSeconds(60)
                .build();

        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOperationsMock = mock(ValueOperations.class);
        when(valueOperationsMock.get(anyString()))
                .thenReturn(mockClient);

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperationsMock);
    }

    @Test
    void shouldExecuteFullEndToEndFlow_WhenRequestIsValid() throws Exception {
        String rawOrderId = UUID.randomUUID().toString();
        UUID generatedOrderId = UUID.randomUUID();

        CreateOrderDTO clientRequest = CreateOrderDTO.builder()
                .internalId(rawOrderId)
                .amount(1000)
                .enableUniqueAmount(true)
                .userId("user_12345")
                .build();

        String method = "POST";
        String path = "/api/v1/orders";
        Instant currentSystemTime = Instant.now();
        String timestamp = String.valueOf(currentSystemTime.getEpochSecond());
        String content = objectMapper.writeValueAsString(clientRequest);

        String dataToSign = String.format("%s|%s|%s|%s", method, path, timestamp, content);
        String validSignature = calculateHmacSha256(dataToSign);

        GetDetailsResponseGrpc mockDetailsResponse = GetDetailsResponseGrpc.newBuilder()
                .setDetails(DetailsGrpc.newBuilder().setBank("Bank").setDetails("details").build())
                .build();
        when(merchantDetailsServiceFutureStub.getDetails(any()))
                .thenReturn(Futures.immediateFuture(mockDetailsResponse));

        OrderResponseDTO mockOrderResponse = new OrderResponseDTO(
                generatedOrderId,
                99L,
                rawOrderId,
                "CREATED",
                1000,
                true,
                null,
                currentSystemTime
        );
        when(orderApi.createOrder(any(CreateOrderRequestDTO.class)))
                .thenReturn(mockOrderResponse);

        try (MockedStatic<Instant> mockedInstant = Mockito.mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
            mockedInstant.when(Instant::now).thenReturn(currentSystemTime);

            webTestClient.post()
                    .uri("/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Api-Key " + API_KEY)
                    .header("Signature", validSignature)
                    .header("X-Timestamp", timestamp)
                    .header("X-Order-Timeout", "120")
                    .header("X-Test-Order", "false")
                    .bodyValue(content)
                    .exchange()

                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.internalId").isEqualTo(rawOrderId)
                    .jsonPath("$.status").isEqualTo("CREATED");
        }
    }

    @Test
    void shouldRedirectToTestOrderFlow_WhenXTestOrderHeaderIsTrue() throws Exception {
        String rawOrderId = UUID.randomUUID().toString();

        CreateOrderDTO clientRequest = CreateOrderDTO.builder()
                .internalId(rawOrderId)
                .amount(5000)
                .enableUniqueAmount(false)
                .userId("test_user_999")
                .build();

        String jsonBody = objectMapper.writeValueAsString(clientRequest);

        Instant currentSystemTime = Instant.now();
        String timestamp = String.valueOf(currentSystemTime.getEpochSecond());
        String method = "POST";
        String path = "/api/v1/orders";

        String dataToSign = String.format("%s|%s|%s|%s", method, path, timestamp, jsonBody);
        String validSignature = calculateHmacSha256(dataToSign);

        webTestClient.post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Api-Key " + API_KEY)
                .header("Signature", validSignature)
                .header("X-Timestamp", timestamp)
                .header("X-Order-Timeout", "60")
                .header("X-Test-Order", "true")
                .bodyValue(jsonBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.internalId").isEqualTo(rawOrderId)
                .jsonPath("$.status").isEqualTo("NEW")
                .jsonPath("$.details.requestMethod").isEqualTo("CARD")
                .jsonPath("$.details.bank").isEqualTo("ALFA")
                .jsonPath("$.details.details").isEqualTo("1111 2222 3333 4444")

                .jsonPath("$.createdAt").exists()
                .jsonPath("$.expiresAt").exists()

                .jsonPath("$").value(responseMap -> {
                    java.util.Map<?, ?> json = (java.util.Map<?, ?>) responseMap;

                    Instant created = Instant.parse(json.get("createdAt").toString());
                    Instant expires = Instant.parse(json.get("expiresAt").toString());
                    org.assertj.core.api.Assertions.assertThat(expires)
                            .isEqualTo(created.plusSeconds(60));
                });
    }

    @Test
    void shouldReturn400BadRequest_WhenValidationFails() throws Exception {
        CreateOrderDTO invalidRequest = CreateOrderDTO.builder()
                .internalId("")
                .amount(-50)
                .build();

        String content = objectMapper.writeValueAsString(invalidRequest);

        String method = "POST";
        String path = "/api/v1/orders";
        Instant currentSystemTime = Instant.now();
        String timestamp = String.valueOf(currentSystemTime.getEpochSecond());

        String dataToSign = String.format("%s|%s|%s|%s", method, path, timestamp, content);
        String validSignature = calculateHmacSha256(dataToSign);

        try (MockedStatic<Instant> mockedInstant = Mockito.mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
            mockedInstant.when(Instant::now).thenReturn(currentSystemTime);

            webTestClient.post()
                    .uri("/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Api-Key " + API_KEY)
                    .header("Signature", validSignature)
                    .header("X-Timestamp", timestamp)
                    .header("X-Order-Timeout", "60")
                    .header("X-Test-Order", "false")
                    .bodyValue(content)
                    .exchange()

                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.title").isEqualTo("Invalid request.")
                    .jsonPath("$.type").isEqualTo("/errors/now-valid")
                    .jsonPath("$.detail").value(detailObj -> {
                        String detail = String.valueOf(detailObj);
                        assertThat(detail).contains("internalId: Обязательно для заполнения");
                        assertThat(detail).contains("amount: Сумма должна быть больше нуля");
                    });
        }
    }

    @Test
    void shouldReturnOrder_WhenGetOrderByIdIsSuccessful() throws Exception {
        UUID orderUuid = UUID.randomUUID();
        String orderId = orderUuid.toString();
        Instant currentSystemTime = Instant.now();

        String method = "GET";
        String path = "/api/v1/orders/" + orderId;
        String timestamp = String.valueOf(currentSystemTime.getEpochSecond());
        String content = "";

        String dataToSign = String.format("%s|%s|%s|%s", method, path, timestamp, content);
        String validSignature = calculateHmacSha256(dataToSign);

        OrderResponseDTO mockOrder = new OrderResponseDTO(
                orderUuid,
                99L,
                "merchant-internal-id-555",
                "PAID",
                1000,
                false,
                null,
                currentSystemTime
        );
        OrdersPageResponseDTO mockPageResponse = new OrdersPageResponseDTO(List.of(mockOrder), 1L);

        when(orderApi.getOrders(any(GetOrdersFilterDTO.class)))
                .thenReturn(mockPageResponse);

        try (MockedStatic<Instant> mockedInstant = Mockito.mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
            mockedInstant.when(Instant::now).thenReturn(currentSystemTime);

            webTestClient.get()
                    .uri("/orders/" + orderId)
                    .header("Authorization", "Api-Key " + API_KEY)
                    .header("Signature", validSignature)
                    .header("X-Timestamp", timestamp)
                    .header("X-Order-Timeout", "300")
                    .exchange()

                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.internalId").isEqualTo("merchant-internal-id-555")
                    .jsonPath("$.status").isEqualTo("PAID");
        }
    }

    @Test
    void shouldReturnOrdersList_WhenPaginationAndSortAreProvided() throws Exception {
        Instant currentSystemTime = Instant.now()
                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        String method = "GET";
        String path = "/api/v1/orders";
        String timestamp = String.valueOf(currentSystemTime.getEpochSecond());
        String content = "";

        String dataToSign = String.format("%s|%s|%s|%s", method, path, timestamp, content);
        String validSignature = calculateHmacSha256(dataToSign);

        OrderResponseDTO mockOrder1 = new OrderResponseDTO(
                UUID.randomUUID(),
                99L,
                "internal-paged-id-1",
                "CREATED",
                1000,
                false,
                null,
                currentSystemTime
        );
        OrderResponseDTO mockOrder2 = new OrderResponseDTO(
                UUID.randomUUID(),
                99L,
                "internal-paged-id-2",
                "PAID",
                2000,
                false,
                null,
                currentSystemTime
        );

        OrdersPageResponseDTO mockPageResponse = new OrdersPageResponseDTO(List.of(mockOrder1, mockOrder2), 2L);

        when(orderApi.getOrders(any(GetOrdersFilterDTO.class)))
                .thenReturn(mockPageResponse);

        try (MockedStatic<Instant> mockedInstant = Mockito.mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
            mockedInstant.when(Instant::now).thenReturn(currentSystemTime);

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/orders")
                            .queryParam("page", "1")
                            .queryParam("size", "15")
                            .queryParam("sort", "createdAt,desc")
                            .build())
                    .header("Authorization", "Api-Key " + API_KEY)
                    .header("Signature", validSignature)
                    .header("X-Timestamp", timestamp)
                    .header("X-Order-Timeout", "60")
                    .exchange()

                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$").isArray()
                    .jsonPath("$[0].internalId").isEqualTo("internal-paged-id-1")
                    .jsonPath("$[0].status").isEqualTo("CREATED")
                    .jsonPath("$[0].expiresAt").isEqualTo(currentSystemTime.plusSeconds(60).toString())
                    .jsonPath("$[1].internalId").isEqualTo("internal-paged-id-2")
                    .jsonPath("$[1].status").isEqualTo("PAID");

            ArgumentCaptor<GetOrdersFilterDTO> filterCaptor = ArgumentCaptor.forClass(GetOrdersFilterDTO.class);
            verify(orderApi).getOrders(filterCaptor.capture());

            GetOrdersFilterDTO actualFilter = filterCaptor.getValue();
            assertThat(actualFilter.clientIds()).contains(99L);
            assertThat(actualFilter.pagination().page()).isEqualTo(1);
            assertThat(actualFilter.pagination().size()).isEqualTo(15);
            assertThat(actualFilter.pagination().sorters()).contains("createdAt,desc");
        }
    }

    @Test
    void shouldCancelOrder_WhenRequestIsValid() throws Exception {
        UUID orderUuid = UUID.randomUUID();
        String orderId = orderUuid.toString();
        Instant currentSystemTime = Instant.now();

        String method = "PATCH";
        String path = "/api/v1/orders/" + orderId;
        String timestamp = String.valueOf(currentSystemTime.getEpochSecond());
        String content = "";

        String dataToSign = String.format("%s|%s|%s|%s", method, path, timestamp, content);
        String validSignature = calculateHmacSha256(dataToSign);

        doNothing().when(orderApi).updateOrderStatus(any(UpdateOrderStatusRequestDTO.class));

        OrderResponseDTO mockCanceledOrder = new OrderResponseDTO(
                orderUuid,
                99L,
                "merchant-internal-id-555",
                "CANCELED",
                1000,
                false,
                null,
                currentSystemTime
        );
        OrdersPageResponseDTO mockPageResponse = new OrdersPageResponseDTO(List.of(mockCanceledOrder), 1L);

        when(orderApi.getOrders(any(GetOrdersFilterDTO.class)))
                .thenReturn(mockPageResponse);

        webTestClient.patch()
                .uri("/orders/" + orderId)
                .header("Authorization", "Api-Key " + API_KEY)
                .header("Signature", validSignature)
                .header("X-Timestamp", timestamp)
                .header("X-Order-Timeout", "180")
                .exchange()

                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.internalId").isEqualTo("merchant-internal-id-555")
                .jsonPath("$.status").isEqualTo("CANCELED")
                .jsonPath("$.createdAt").exists()
                .jsonPath("$.expiresAt").exists()
                .jsonPath("$").value(responseMap -> {
                    java.util.Map<?, ?> json = (java.util.Map<?, ?>) responseMap;
                    Instant created = Instant.parse(json.get("createdAt").toString());
                    Instant expires = Instant.parse(json.get("expiresAt").toString());

                    assertThat(expires).isEqualTo(created.plusSeconds(180));
                });

        ArgumentCaptor<UpdateOrderStatusRequestDTO> statusCaptor = ArgumentCaptor.forClass(
                UpdateOrderStatusRequestDTO.class);
        verify(orderApi).updateOrderStatus(statusCaptor.capture());
        assertThat(statusCaptor.getValue().id()).isEqualTo(orderUuid);
        assertThat(statusCaptor.getValue().status()).isEqualTo("CANCELED");
        assertThat(statusCaptor.getValue().clientId()).isEqualTo(99L);
    }

}
