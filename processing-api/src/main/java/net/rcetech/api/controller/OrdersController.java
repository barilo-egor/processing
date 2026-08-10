package net.rcetech.api.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import net.rcetech.api.config.security.ApiSignatureFilter;
import net.rcetech.api.exceptions.OrderNotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;
import net.rcetech.api.dto.ClientByApiKeyDTO;
import net.rcetech.api.dto.CreateOrderDTO;
import net.rcetech.api.dto.OrderResponseDTO;
import net.rcetech.api.service.OrderService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/orders")
public class OrdersController {

    private final OrderService orderService;

    public OrdersController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Создает новый заказ или запускает тестовую симуляцию заказа.
     * <p>
     * Метод защищен фильтром криптографической подписи. Если заголовок {@code X-Test-Order}
     * передан как {@code true}, запрос обрабатывается локально без обращения к gRPC-сервисам.
     * В противном случае выполняется сквозной бизнес-флоу интеграции с gRPC.
     *
     * @param clientRequest      валидный DTO-запрос с параметрами создаваемого заказа.
     * @param isTestOrder        необязательный флаг для переключения в режим локального тестирования.
     * @param clientOrderTimeout максимальное время жизни заказа в секундах, формируется в gateway.
     * @param client             авторизованный клиент, автоматически извлеченный
     *                           {@link ApiSignatureFilter}.
     * @return {@link OrderResponseDTO} с деталями созданного или симулированного заказа.
     */
    @PostMapping
    public OrderResponseDTO createOrder(@Valid @RequestBody CreateOrderDTO clientRequest,
            @RequestHeader(value = "X-Test-Order", required = false) String isTestOrder,
            @RequestHeader(value = "X-Order-Timeout") Integer clientOrderTimeout,
            @RequestAttribute("authenticatedClient") ClientByApiKeyDTO client) {
        if (Boolean.parseBoolean(isTestOrder)) {
            log.info("Получен тестовый запрос (X-Test-Order = true) для клиента {}.", client);
            return orderService.testOrder(clientRequest, clientOrderTimeout);
        }
        return orderService.createOrder(clientRequest, client, clientOrderTimeout);
    }

    /**
     * Возвращает детали заказа по его идентификатору.
     * <p>
     * Поиск выполняется через gRPC-сервис заказов сначала по системному ID,
     * а затем по внешнему (клиентскому) ID. На основе времени создания
     * и переданного таймаута рассчитывается срок жизни заказа.
     *
     * @param id                 системный или внешний идентификатор заказа.
     * @param clientOrderTimeout максимальное время жизни заказа в секундах.
     * @param client             авторизованный клиент, автоматически извлеченный
     *                           {@link ApiSignatureFilter}.
     * @return {@link OrderResponseDTO} с актуальным статусом и деталями заказа.
     * @throws OrderNotFoundException если заказ не найден ни по одному из идентификаторов.
     */
    @GetMapping("/{id}")
    public OrderResponseDTO getOrder(@PathVariable String id,
            @RequestHeader(value = "X-Order-Timeout") Integer clientOrderTimeout,
            @RequestAttribute("authenticatedClient") ClientByApiKeyDTO client) {
        return orderService.findOrder(id, clientOrderTimeout, client);
    }

    /**
     * Возвращает список заказов текущего клиента с поддержкой пагинации и сортировки.
     * <p>
     * Метод трансформирует параметры {@link Pageable} в Protobuf-структуру и запрашивает
     * данные через gRPC-сервис заказов. Для каждого заказа рассчитывается индивидуальный
     * срок жизни на основе переданного таймаута.
     *
     * @param clientOrderTimeout максимальное время жизни заказа в секундах для расчета срока экспирации.
     * @param client             авторизованный клиент, автоматически извлеченный
     *                           {@link ApiSignatureFilter}.
     * @param pageable           параметры пагинации и сортировки (по умолчанию размер страницы — 25).
     * @return список {@link OrderResponseDTO}, мапируемый на выходе в JSON-массив.
     */
    @GetMapping
    public List<OrderResponseDTO> findOrders(@RequestHeader(value = "X-Order-Timeout") Integer clientOrderTimeout,
            @RequestAttribute("authenticatedClient") ClientByApiKeyDTO client,
            @PageableDefault(size = 25) Pageable pageable) {
        return orderService.findOrders(clientOrderTimeout, client, pageable);
    }

    /**
     * Выполняет отмену заказа по его идентификатору.
     * <p>
     * Метод отправляет gRPC-запрос для перевода статуса заказа в {@code CANCELED}.
     * После успешного обновления статуса выполняется повторный запрос деталей
     * заказа для формирования актуального ответа клиенту.
     *
     * @param id                 идентификатор отменяемого заказа.
     * @param clientOrderTimeout максимальное время жизни заказа в секундах для перерасчета срока экспирации.
     * @param client             авторизованный клиент, автоматически извлеченный
     *                           {@link ApiSignatureFilter}.
     * @return {@link OrderResponseDTO} с обновленным статусом заказа.
     * @throws OrderNotFoundException если отменяемый заказ не найден в системе.
     */
    @PatchMapping("/{id}")
    public OrderResponseDTO cancelOrder(@PathVariable String id,
            @RequestHeader(value = "X-Order-Timeout") Integer clientOrderTimeout,
            @RequestAttribute("authenticatedClient") ClientByApiKeyDTO client) {
        return orderService.cancelOrder(id, clientOrderTimeout, client);
    }

}
