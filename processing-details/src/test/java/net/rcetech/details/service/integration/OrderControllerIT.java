package net.rcetech.details.service.integration;

import com.google.common.util.concurrent.Futures;
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import tgb.cryptoexchange.grpc.generated.*;
import net.rcetech.details.dto.ClientByApiKeyDTO;
import net.rcetech.details.dto.CreateOrderDTO;
import net.rcetech.details.enums.ClientStatus;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

        CreateOrderResponseGrpc mockOrdersResponse = CreateOrderResponseGrpc.newBuilder()
                .setId(rawOrderId)
                .setClientId(99L)
                .setInternalId(rawOrderId)
                .setStatus("CREATED")
                .setCreatedAt(Timestamp.newBuilder().setSeconds(currentSystemTime.getEpochSecond()).setNanos(0).build())
                .build();
        when(apiOrdersServiceFutureStub.createOrder(any()))
                .thenReturn(Futures.immediateFuture(mockOrdersResponse));

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
        String orderId = "12345";
        Instant currentSystemTime = Instant.now();

        String method = "GET";
        String path = "/api/v1/orders/" + orderId;
        String timestamp = String.valueOf(currentSystemTime.getEpochSecond());
        String content = "";

        String dataToSign = String.format("%s|%s|%s|%s", method, path, timestamp, content);
        String validSignature = calculateHmacSha256(dataToSign);

        GetOrdersResponseGrpc mockOrdersResponse = GetOrdersResponseGrpc.newBuilder()
                .setTotalElements(1L)
                .addOrders(OrderResponse.newBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setInternalId("merchant-internal-id-555")
                        .setStatus("PAID")
                        .build())
                .build();

        when(apiOrdersServiceFutureStub.getOrders(any()))
                .thenReturn(Futures.immediateFuture(mockOrdersResponse));

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

        GetOrdersResponseGrpc mockOrdersPageResponse = GetOrdersResponseGrpc.newBuilder()
                .setTotalElements(2L)
                .addOrders(OrderResponse.newBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setInternalId("internal-paged-id-1")
                        .setStatus("CREATED")
                        .setCreatedAt(Timestamp.newBuilder().setSeconds(currentSystemTime.getEpochSecond()).setNanos(0)
                                .build())
                        .build())
                .addOrders(OrderResponse.newBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setInternalId("internal-paged-id-2")
                        .setStatus("PAID")
                        .setCreatedAt(Timestamp.newBuilder().setSeconds(currentSystemTime.getEpochSecond()).setNanos(0)
                                .build())
                        .build())
                .build();

        when(apiOrdersServiceFutureStub.getOrders(any(GetOrdersGrpc.class)))
                .thenReturn(Futures.immediateFuture(mockOrdersPageResponse));

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

            org.mockito.ArgumentCaptor<GetOrdersGrpc> requestCaptor = org.mockito.ArgumentCaptor.forClass(
                    GetOrdersGrpc.class);
            Mockito.verify(apiOrdersServiceFutureStub).getOrders(requestCaptor.capture());

            GetOrdersGrpc actualGrpcRequest = requestCaptor.getValue();
            assertThat(actualGrpcRequest.getClientIdsList()).contains(99L);
            assertThat(actualGrpcRequest.getPagination().getPage()).isEqualTo(1);
            assertThat(actualGrpcRequest.getPagination().getSize()).isEqualTo(15);
            assertThat(actualGrpcRequest.getPagination().getSortersList()).contains("createdAt,desc");
        }
    }

    @Test
    void shouldCancelOrder_WhenRequestIsValid() throws Exception {
        String orderId = "98765";
        Instant currentSystemTime = Instant.now();

        String method = "PATCH";
        String path = "/api/v1/orders/" + orderId;
        String timestamp = String.valueOf(currentSystemTime.getEpochSecond());
        String content = "";

        String dataToSign = String.format("%s|%s|%s|%s", method, path, timestamp, content);
        String validSignature = calculateHmacSha256(dataToSign);

        com.google.protobuf.Empty emptyResponse = com.google.protobuf.Empty.getDefaultInstance();
        when(apiOrdersServiceFutureStub.updateOrderStatus(any(UpdateOrderStatusGrpc.class)))
                .thenReturn(com.google.common.util.concurrent.Futures.immediateFuture(emptyResponse));

        GetOrdersResponseGrpc mockOrdersResponse =
                GetOrdersResponseGrpc.newBuilder()
                        .setTotalElements(1L)
                        .addOrders(OrderResponse.newBuilder()
                                .setId(UUID.randomUUID().toString())
                                .setInternalId("merchant-internal-id-555")
                                .setStatus("CANCELED")
                                .build())
                        .build();

        when(apiOrdersServiceFutureStub.getOrders(any(GetOrdersGrpc.class)))
                .thenReturn(com.google.common.util.concurrent.Futures.immediateFuture(mockOrdersResponse));

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

                    org.assertj.core.api.Assertions.assertThat(expires)
                            .isEqualTo(created.plusSeconds(180));
                });
    }

}
