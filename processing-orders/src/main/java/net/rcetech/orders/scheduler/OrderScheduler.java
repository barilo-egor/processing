package net.rcetech.orders.scheduler;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.orders.OrderStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
@Profile("!test")
public class OrderScheduler {

    private final OrderService orderService;

    private final ClientService clientService;

    public OrderScheduler(OrderService orderService, ClientService clientService) {
        this.orderService = orderService;
        this.clientService = clientService;
    }

    /**
     * Периодическая проверка и перевод в статус {@link OrderStatus#TIMEOUT}
     * всех ордеров со статусом {@link OrderStatus#NEW}, у которых истекло время ожидания.
     * <p>
     */
    @Scheduled(fixedDelay = 1000)
    public void checkOrdersTimeout() {
        Specification<Order> buildFindSpecification =
                (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), OrderStatus.NEW);
        List<Order> orderList = orderService.findOrderByField(buildFindSpecification);
        Instant now = Instant.now();
        for (Order order : orderList) {
            try {
                Optional<Client> maybeClient = clientService.findById(order.getClientId());
                if (maybeClient.isEmpty() || maybeClient.get().getOrderTimeoutSeconds() == null) {
                    continue;
                }
                Client client = maybeClient.get();
                Instant timeoutExpirationTime = order.getCreatedAt().plusSeconds(client.getOrderTimeoutSeconds());
                if (timeoutExpirationTime.isBefore(now)) {
                    orderService.updateStatus(order.getId().toString(), OrderStatus.TIMEOUT);
                }
            } catch (Exception e) {
                log.error("Ошибка при обработке checkOrdersTimeout {}", order.getId(), e);
            }
        }
    }

}
