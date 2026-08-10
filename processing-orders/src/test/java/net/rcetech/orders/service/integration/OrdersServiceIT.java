package net.rcetech.orders.service.integration;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import net.rcetech.meta.clients.service.ClientApi;
import net.rcetech.orders.dto.OrderDTO;
import net.rcetech.orders.entity.Order;
import net.rcetech.orders.enums.OrderStatus;
import net.rcetech.orders.exceptions.NotFoundException;
import net.rcetech.orders.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import net.rcetech.meta.orders.dto.GetOrdersFilterDTO;
import net.rcetech.meta.orders.dto.OrderResponseDTO;
import net.rcetech.meta.orders.dto.OrdersPageResponseDTO;
import net.rcetech.meta.orders.dto.PaginationParamsDTO;
import net.rcetech.meta.orders.service.OrderApi;
import tgb.cryptoexchange.commons.enums.Merchant;

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
    private OrderApi ordersApi;

    @MockitoBean
    private ClientApi clientApi;

    @Autowired
    private ApplicationEvents applicationEvents;

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

        GetOrdersFilterDTO filter = new GetOrdersFilterDTO(
                new PaginationParamsDTO(0, 2, List.of("amount,desc")),
                null,
                List.of(targetClientId),
                null,
                null,
                null,
                null,
                null,
                null
        );

        OrdersPageResponseDTO response = ordersApi.getOrders(filter);
        assertThat(response).isNotNull();
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.orders()).hasSize(2);

        var firstResult = response.orders().get(0);
        var secondResult = response.orders().get(1);

        assertThat(firstResult.id()).isEqualTo(order2.getId());
        assertThat(firstResult.amount()).isEqualTo(5000);

        assertThat(secondResult.id()).isEqualTo(order3.getId());
        assertThat(secondResult.amount()).isEqualTo(3000);

        boolean containsOtherClient = response.orders().stream()
                .anyMatch(o -> otherClientId.equals(o.clientId()));
        assertThat(containsOtherClient).isFalse();
    }

    @Test
    @DisplayName("Возврат пустой страницы, если под фильтры ничего не подходит")
    void shouldReturnEmptyPageWhenNoOrdersMatchFilters() {
        Order order = createOrder(500L, 100, Instant.now());
        orderRepository.saveAndFlush(order);

        GetOrdersFilterDTO filter = new GetOrdersFilterDTO(
                new PaginationParamsDTO(0, 10, List.of()),
                null,
                List.of(999L),
                null,
                null,
                null,
                null,
                null,
                null
        );

        OrdersPageResponseDTO response = ordersApi.getOrders(filter);

        assertThat(response).isNotNull();
        assertThat(response.totalElements()).isZero();
        assertThat(response.orders()).isEmpty();
    }

    @Test
    @DisplayName("Успешный возврат данных с дефолтной пагинацией, если блок pagination не передан в запросе")
    void shouldReturnDefaultPageWhenPaginationParamsAreMissing() {
        Instant baseTime = Instant.now().truncatedTo(ChronoUnit.MICROS);

        Order order1 = createOrder(300L, 1000, baseTime);
        Order order2 = createOrder(300L, 2000, baseTime);
        Order order3 = createOrder(300L, 3000, baseTime);

        orderRepository.saveAllAndFlush(List.of(order1, order2, order3));

        GetOrdersFilterDTO filter = new GetOrdersFilterDTO(
                new PaginationParamsDTO(0, 20, List.of()),
                null,
                List.of(300L),
                null,
                null,
                null,
                null,
                null,
                null
        );

        OrdersPageResponseDTO response = ordersApi.getOrders(filter);

        assertThat(response).isNotNull();
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.orders()).hasSize(3);

        List<UUID> returnedIds = response.orders().stream()
                .map(OrderResponseDTO::id)
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
