package net.rcetech.orders.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import net.rcetech.orders.dto.ClientDTO;
import net.rcetech.orders.entity.Order;
import net.rcetech.orders.enums.OrderStatus;
import net.rcetech.orders.service.ApiClientsGrpcService;
import net.rcetech.orders.service.OrderService;

import java.time.Instant;
import java.util.List;

@Component
@Slf4j
@Profile("!test")
public class OrderScheduler {

    private final OrderService orderService;

    private final ApiClientsGrpcService apiClientsGrpcService;

    public OrderScheduler(OrderService orderService, ApiClientsGrpcService apiClientsGrpcService) {
        this.orderService = orderService;
        this.apiClientsGrpcService = apiClientsGrpcService;
    }

    /**
     * Периодическая проверка и перевод в статус {@link OrderStatus#TIMEOUT}
     * всех ордеров со статусом {@link OrderStatus#NEW}, у которых истекло время ожидания.
     * <p>
     * Метод ежесекундно выбирает ордеры, запрашивает
     * индивидуальные настройки таймаута для каждого клиента через внешний gRPC-сервис
     * и обновляет статус просроченных сущностей.
     */
    @Scheduled(fixedDelay = 1000)
    public void checkOrdersTimeout() {
        Specification<Order> buildFindSpecification =
                (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), OrderStatus.NEW);
        List<Order> orderList = orderService.findOrderByField(buildFindSpecification);
        Instant now = Instant.now();
        for (Order order : orderList) {
            try {
                ClientDTO clientDTO = apiClientsGrpcService.getClientById(order.getClientId()).block();
                if (clientDTO == null || clientDTO.getOrderTimeoutSeconds() == null) {
                    continue;
                }
                Instant timeoutExpirationTime = order.getCreatedAt().plusSeconds(clientDTO.getOrderTimeoutSeconds());
                if (timeoutExpirationTime.isBefore(now)) {
                    orderService.updateStatus(order.getId().toString(), OrderStatus.TIMEOUT);
                }
            } catch (Exception e) {
                log.error("Ошибка при обработке checkOrdersTimeout {}", order.getId(), e);
            }
        }
    }

}
