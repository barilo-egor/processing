package tgb.cryptoexchange.orders.service.integration;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import tgb.cryptoexchange.commons.enums.Merchant;
import tgb.cryptoexchange.grpc.generated.GetOrdersGrpc;
import tgb.cryptoexchange.grpc.generated.GetOrdersResponseGrpc;
import tgb.cryptoexchange.grpc.generated.OrdersServiceGrpc;
import tgb.cryptoexchange.grpc.generated.PaginationParams;
import tgb.cryptoexchange.orders.dto.OrderDTO;
import tgb.cryptoexchange.orders.entity.Order;
import tgb.cryptoexchange.orders.enums.OrderStatus;
import tgb.cryptoexchange.orders.exceptions.NotFoundException;
import tgb.cryptoexchange.orders.service.OrderService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class OrdersServiceIT extends BaseIntegrationTest {

    private final TimeBasedEpochGenerator generator = Generators.timeBasedEpochGenerator();

    @Autowired
    private OrderService orderService;

    @Autowired
    private ApplicationEvents applicationEvents;

    private OrdersServiceGrpc.OrdersServiceBlockingStub blockingStub;

    @BeforeEach
    void initStub() {
        blockingStub = OrdersServiceGrpc.newBlockingStub(channel);
    }

    @Test
    @DisplayName("Успешное создание заказа с валидными данными")
    void shouldCreateOrderSuccessfully() {
        OrderDTO requestDto = OrderDTO.builder()
                .id(generator.generate())
                .clientId(42L)
                .internalId("unique-internal-id-001")
                .amount(1500)
                .enableUniqueAmount(true)
                .callbackUrl("https://example.com")
                .merchantOrderId("123n45")
                .merchant(Merchant.ALFA_TEAM)
                .merchantOrderStatus("APPROVED")
                .status(OrderStatus.SUCCESS)
                .build();

        OrderDTO responseDto = orderService.create(requestDto);

        assertThat(responseDto).isNotNull();
        assertThat(responseDto.getId()).isNotNull();
        assertThat(responseDto.getClientId()).isEqualTo(42L);
        assertThat(responseDto.getInternalId()).isEqualTo("unique-internal-id-001");
        assertThat(responseDto.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(responseDto.getAmount()).isEqualTo(1500);
        assertThat(responseDto.getEnableUniqueAmount()).isTrue();
        assertThat(responseDto.getCallbackUrl()).isEqualTo("https://example.com");
        assertThat(responseDto.getCreatedAt()).isNotNull();

        Optional<Order> dbOrderOpt = orderRepository.findById(responseDto.getId());
        assertThat(dbOrderOpt).isPresent();

        Order dbOrder = dbOrderOpt.get();
        assertThat(dbOrder.getClientId()).isEqualTo(42L);
        assertThat(dbOrder.getInternalId()).isEqualTo("unique-internal-id-001");
        assertThat(dbOrder.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(dbOrder.getAmount()).isEqualTo(1500);
        assertThat(dbOrder.getEnableUniqueAmount()).isTrue();
        assertThat(dbOrder.getCallbackUrl()).isEqualTo("https://example.com");
        assertThat(dbOrder.getCreatedAt()).isCloseTo(responseDto.getCreatedAt(), within(1, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("Успешное обновление статуса существующего заказа и публикация события")
    void shouldUpdateStatusAndPublishEventSuccessfully() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .clientId(777L)
                .internalId("internal-update-1")
                .merchant(Merchant.ALFA_TEAM)
                .merchantOrderStatus("qwerty")
                .merchantOrderId("12345")
                .status(OrderStatus.NEW)
                .amount(1000)
                .build();
        orderRepository.saveAndFlush(order);

        orderService.updateStatus(orderId.toString(), OrderStatus.TIMEOUT);

        Optional<Order> updatedOrderOpt = orderRepository.findById(orderId);
        assertThat(updatedOrderOpt).isPresent();
        assertThat(updatedOrderOpt.get().getStatus()).isEqualTo(OrderStatus.TIMEOUT);

        long matchingEventsCount = applicationEvents.stream(OrderDTO.class)
                .filter(eventDto -> eventDto.getId().equals(orderId) && eventDto.getStatus() == OrderStatus.TIMEOUT)
                .count();

        assertThat(matchingEventsCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Выброс NotFoundException при попытке обновить несуществующий заказ")
    void shouldThrowNotFoundExceptionWhenOrderDoesNotExist() {
        UUID nonExistingId = UUID.randomUUID();

        String id = nonExistingId.toString();

        assertThatThrownBy(() -> orderService.updateStatus(id, OrderStatus.SUCCESS))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Record not found for the provided ID.");

        long eventsCount = applicationEvents.stream(OrderDTO.class).count();
        assertThat(eventsCount).isZero();
    }

    @Test
    @DisplayName("Успешная пагинация, фильтрация по clientId и сортировка по сумме (DESC)")
    void shouldFilterPaginateAndSortOrdersSuccessfully() {
        Long targetClientId = 100L;
        Long otherClientId = 200L;
        Instant baseTime = Instant.now().truncatedTo(ChronoUnit.MICROS);

        Order order1 = createOrder(targetClientId, 1000, baseTime.minusSeconds(10));
        Order order2 = createOrder(targetClientId, 5000, baseTime.minusSeconds(5));
        Order order3 = createOrder(targetClientId, 3000, baseTime);
        Order otherOrder = createOrder(otherClientId, 9999, baseTime);

        orderRepository.saveAllAndFlush(List.of(order1, order2, order3, otherOrder));

        GetOrdersGrpc request = GetOrdersGrpc.newBuilder()
                .setPagination(PaginationParams.newBuilder()
                        .setPage(0)
                        .setSize(2)
                        .addSorters("amount,desc")
                        .build())
                .addClientIds(targetClientId)
                .build();

        GetOrdersResponseGrpc response = blockingStub.getOrders(request);
        assertThat(response).isNotNull();
        assertThat(response.getTotalElements()).isEqualTo(3);
        assertThat(response.getOrdersCount()).isEqualTo(2);

        var firstResult = response.getOrders(0);
        var secondResult = response.getOrders(1);

        assertThat(UUID.fromString(firstResult.getId())).isEqualTo(order2.getId());
        assertThat(firstResult.getAmount()).isEqualTo(5000);

        assertThat(UUID.fromString(secondResult.getId())).isEqualTo(order3.getId());
        assertThat(secondResult.getAmount()).isEqualTo(3000);

        boolean containsOtherClient = response.getOrdersList().stream()
                .anyMatch(o -> otherClientId.equals(o.getClientId()));
        assertThat(containsOtherClient).isFalse();
    }

    @Test
    @DisplayName("Возврат пустой страницы, если под фильтры ничего не подходит")
    void shouldReturnEmptyPageWhenNoOrdersMatchFilters() {
        Order order = createOrder(500L, 100, Instant.now());
        orderRepository.saveAndFlush(order);

        GetOrdersGrpc request = GetOrdersGrpc.newBuilder()
                .setPagination(PaginationParams.newBuilder().setPage(0).setSize(10).build())
                .addClientIds(999L)
                .build();

        GetOrdersResponseGrpc response = blockingStub.getOrders(request);

        assertThat(response).isNotNull();
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.getOrdersCount()).isZero();
    }

    @Test
    @DisplayName("Успешный возврат данных с дефолтной пагинацией, если блок pagination не передан в запросе")
    void shouldReturnDefaultPageWhenPaginationParamsAreMissing() {
        Instant baseTime = Instant.now().truncatedTo(ChronoUnit.MICROS);

        Order order1 = createOrder(300L, 1000, baseTime);
        Order order2 = createOrder(300L, 2000, baseTime);
        Order order3 = createOrder(300L, 3000, baseTime);

        orderRepository.saveAllAndFlush(List.of(order1, order2, order3));

        GetOrdersGrpc request = GetOrdersGrpc.newBuilder()
                .addClientIds(300L)
                .build();

        GetOrdersResponseGrpc response = blockingStub.getOrders(request);

        assertThat(response).isNotNull();
        assertThat(response.getTotalElements()).isEqualTo(3);
        assertThat(response.getOrdersCount()).isEqualTo(3);

        List<UUID> returnedIds = response.getOrdersList().stream()
                .map(o -> UUID.fromString(o.getId()))
                .toList();

        assertThat(returnedIds).containsExactlyInAnyOrderElementsOf(returnedIds);
    }

    private Order createOrder(Long clientId, Integer amount, Instant createdAt) {
        return Order.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .internalId("internal-" + UUID.randomUUID().toString().substring(0, 8))
                .status(OrderStatus.NEW)
                .amount(amount)
                .merchant(Merchant.ALFA_TEAM)
                .merchantOrderStatus("qwerty")
                .merchantOrderId("12345")
                .createdAt(createdAt)
                .enableUniqueAmount(false)
                .build();
    }

}
