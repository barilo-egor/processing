package net.rcetech.orders.service.integration;

import net.rcetech.meta.clients.dto.ClientResponseDTO;
import net.rcetech.meta.clients.dto.CreateSignatureDTO;
import net.rcetech.meta.clients.service.ClientApi;
import net.rcetech.orders.entity.Order;
import net.rcetech.orders.enums.OrderStatus;
import net.rcetech.orders.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;

class OrderCallbackNotificationIT extends BaseIntegrationTest {

    @Autowired
    private OrderService orderService;

    @MockitoBean
    private ClientApi clientApi;

    @Test
    @DisplayName("Успешный полный цикл: получение URL через ClientApi -> подпись через ClientApi -> отправка по WebClient")
    void shouldExecuteFullCallbackPipelineSuccessfully() {
        UUID orderId = UUID.randomUUID();
        Long clientId = 999L;
        String callbackPath = "/api/v1/callback";
        String fullCallbackUrl = "http://localhost:" + wireMockServer.port() + callbackPath;
        String mockSignature = "mocked-secure-digital-signature-12345";

        ClientResponseDTO mockClientDto = new ClientResponseDTO(
                clientId,
                "test_user",
                "secret",
                "preview",
                Instant.now(),
                "ACTIVE",
                fullCallbackUrl,
                300
        );

        Mockito.when(clientApi.getClientById(clientId))
                .thenReturn(mockClientDto);

        Mockito.when(clientApi.createSignature(any(CreateSignatureDTO.class)))
                .thenReturn(mockSignature);

        wireMockClient.register(post(urlEqualTo(callbackPath))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")));

        Order order = Order.builder()
                .id(orderId)
                .clientId(clientId)
                .internalId("internal-callback-it-1")
                .status(OrderStatus.NEW)
                .amount(5000)
                .merchant(Merchant.ALFA_TEAM)
                .merchantOrderStatus("qwerty")
                .merchantOrderId("12345")
                .callbackUrl(null)
                .build();
        orderRepository.saveAndFlush(order);

        orderService.updateStatus(orderId.toString(), OrderStatus.SUCCESS);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> verify(postRequestedFor(urlEqualTo(callbackPath))
                .withHeader("Signature", equalTo(mockSignature))
                .withHeader("X-Timestamp", matching("\\d+"))
                .withRequestBody(containing(orderId.toString()))
                .withRequestBody(containing("5000"))
        ));

        Mockito.verify(clientApi, Mockito.times(1)).getClientById(clientId);
        Mockito.verify(clientApi, Mockito.times(1))
                .createSignature(
                        argThat(dto -> dto.clientId().equals(clientId) && dto.data().contains("POST|" + callbackPath)));
    }

    @Test
    @DisplayName("Пропуск вызова getClientById, если callbackUrl уже заполнен в самом order")
    void shouldSkipClientFetchIfCallbackUrlIsAlreadyPresent() {
        UUID orderId = UUID.randomUUID();
        Long clientId = 888L;
        String callbackPath = "/api/v2/direct-callback";
        String fullCallbackUrl = "http://localhost:" + wireMockServer.port() + callbackPath;
        String mockSignature = "direct-signature";

        Mockito.when(clientApi.createSignature(any(CreateSignatureDTO.class)))
                .thenReturn(mockSignature);

        wireMockClient.register(post(urlEqualTo(callbackPath)).willReturn(aResponse().withStatus(200)));

        Order order = Order.builder()
                .id(orderId)
                .clientId(clientId)
                .internalId("internal-callback-it-2")
                .status(OrderStatus.NEW)
                .amount(100)
                .merchant(Merchant.ALFA_TEAM)
                .merchantOrderStatus("qwerty")
                .merchantOrderId("12345")
                .callbackUrl(fullCallbackUrl)
                .build();
        orderRepository.saveAndFlush(order);

        orderService.updateStatus(orderId.toString(), OrderStatus.SUCCESS);

        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> verify(postRequestedFor(urlEqualTo(callbackPath))
                .withHeader("Signature", equalTo(mockSignature))
        ));

        Mockito.verify(clientApi, Mockito.never()).getClientById(anyLong());
    }

    @Test
    @DisplayName("Обработка ошибки: сервер клиента вернул 500 Internal Server Error")
    void shouldHandleHttp500ErrorGracefully() {
        UUID orderId = UUID.randomUUID();
        Long clientId = 111L;
        String callbackPath = "/api/v1/error-callback";

        String fullCallbackUrl = "http://localhost:" + wireMockServer.port() + callbackPath;
        String mockSignature = "error-test-signature";

        Mockito.when(clientApi.createSignature(any(CreateSignatureDTO.class)))
                .thenReturn(mockSignature);

        wireMockClient.register(post(urlEqualTo(callbackPath))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Internal Server Error\"}")));

        Order order = Order.builder()
                .id(orderId)
                .clientId(clientId)
                .internalId("internal-callback-err-1")
                .status(OrderStatus.NEW)
                .merchant(Merchant.ALFA_TEAM)
                .merchantOrderStatus("qwerty")
                .merchantOrderId("12345")
                .amount(5000)
                .callbackUrl(fullCallbackUrl)
                .build();
        orderRepository.saveAndFlush(order);

        orderService.updateStatus(orderId.toString(), OrderStatus.SUCCESS);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> verify(postRequestedFor(urlEqualTo(callbackPath))
                .withHeader("Signature", equalTo(mockSignature))
        ));
    }

    @Test
    @DisplayName("Корректная обработка ошибки и логирование, если URL невалиден и ломает URI.create()")
    void shouldHandleExceptionWhenUrlIsMalformed() {
        UUID orderId = UUID.randomUUID();
        Long clientId = 502L;
        String invalidUrl = "http://^invalid-url-format.com";

        Order order = Order.builder()
                .id(orderId)
                .clientId(clientId)
                .internalId("internal-callback-malformed")
                .status(OrderStatus.NEW)
                .merchant(Merchant.ALFA_TEAM)
                .merchantOrderStatus("qwerty")
                .merchantOrderId("12345")
                .amount(1000)
                .callbackUrl(invalidUrl)
                .build();
        orderRepository.saveAndFlush(order);

        orderService.updateStatus(orderId.toString(), OrderStatus.SUCCESS);

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> verify(0, postRequestedFor(anyUrl())));

        Mockito.verify(clientApi, Mockito.never()).createSignature(any(CreateSignatureDTO.class));
    }

}
