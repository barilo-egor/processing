package net.rcetech.orders.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import net.rcetech.orders.config.MerchantStatusProperties;
import net.rcetech.orders.dto.OrderDTO;
import net.rcetech.orders.entity.Order;
import net.rcetech.orders.enums.OrderStatus;
import net.rcetech.orders.exceptions.AlreadyExistsException;
import net.rcetech.orders.exceptions.NotFoundException;
import net.rcetech.orders.kafka.MerchantCallbackEvent;
import net.rcetech.orders.kafka.MerchantUnknownStatusService;
import net.rcetech.orders.mapper.OrderMapper;
import net.rcetech.orders.repository.OrderRepository;
import net.rcetech.orders.utils.PageableUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;

    private final OrderMapper orderMapper;

    private final ApplicationEventPublisher eventPublisher;

    private final MerchantStatusProperties merchantStatusProperties;

    private final MerchantUnknownStatusService merchantUnknownStatusService;

    public OrderService(OrderRepository orderRepository, OrderMapper orderMapper,
            ApplicationEventPublisher eventPublisher, MerchantStatusProperties merchantStatusProperties,
            MerchantUnknownStatusService merchantUnknownStatusService) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.eventPublisher = eventPublisher;
        this.merchantStatusProperties = merchantStatusProperties;
        this.merchantUnknownStatusService = merchantUnknownStatusService;
    }

    /**
     * Создает новый order в системе с принудительной инициализацией статуса {@link OrderStatus#NEW}.
     *
     * @param orderDTO данные для создания нового order
     * @return {@link OrderDTO} созданного order с заполненным идентификатором и временем создания
     * @throws AlreadyExistsException если order с переданным {@code internalId} уже зарегистрирован в базе данных
     */
    public OrderDTO create(OrderDTO orderDTO) {
        log.debug("Запрос на создание order: {}", orderDTO);
        if (orderRepository.existsByInternalId(orderDTO.getInternalId())) {
            throw new AlreadyExistsException(orderDTO.getInternalId());
        }
        Order order = Order.builder()
                .id(orderDTO.getId())
                .clientId(orderDTO.getClientId())
                .internalId(orderDTO.getInternalId())
                .status(OrderStatus.NEW)
                .amount(orderDTO.getAmount())
                .enableUniqueAmount(orderDTO.getEnableUniqueAmount())
                .merchant(orderDTO.getMerchant())
                .merchantOrderId(orderDTO.getMerchantOrderId())
                .merchantOrderStatus(orderDTO.getMerchantOrderStatus())
                .callbackUrl(orderDTO.getCallbackUrl())
                .build();

        order = orderRepository.save(order);
        log.debug("Создан order: {}", order.getId());
        return orderMapper.entityToDTO(order);
    }

    public void updateStatus(String id, OrderStatus newStatus) {
        updateStatus(id, null, newStatus);
    }

    /**
     * Обновляет статус существующего order и публикует событие об изменении его состояния.
     *
     * @param id        уникальный идентификатор или internalId order
     * @param clientId  идентификатор клиента в api-clients
     * @param newStatus новый устанавливаемый статус
     * @throws NotFoundException если order с указанным {@code id} не найден в базе данных
     */
    public void updateStatus(String id, Long clientId, OrderStatus newStatus) {
        log.debug("Запрос на обновление статуса order, id={}, newStatus={}", id, newStatus);
        UUID maybeOrderId = UUID.fromString(id);
        int result = Objects.isNull(clientId) ? orderRepository.updateStatusById(maybeOrderId, newStatus) :
                orderRepository.updateStatusByIdAndClientId(maybeOrderId, clientId, newStatus);
        if (result == 0) {
            result = Objects.isNull(clientId) ? orderRepository.updateStatusByInternalId(id, newStatus) :
                    orderRepository.updateStatusByInternalIdAndClientId(id, clientId, newStatus);
            if (result == 0) {
                throw new NotFoundException(id);
            }
            Order order = orderRepository.getOrdersByInternalId(id);
            maybeOrderId = order.getId();
        }

        if (eventPublisher != null) {
            eventPublisher.publishEvent(orderMapper.entityToDTO(orderRepository.getOrdersById(maybeOrderId)));
        }
    }

    /**
     * Обновляет статус order на основе события от мерчанта.
     * <p>
     *
     * @param id    уникальный идентификатор order
     * @param event объект события с данными от мерчанта
     */
    public void updateStatusByMerchantStatus(UUID id, MerchantCallbackEvent event) {
        log.debug("Запрос на обновление статуса order от merchantCallback, id={}, merchantCallback={}", id, event);
        if (merchantStatusProperties.isSuccess(event.getStatus())) {
            updateStatus(id.toString(), OrderStatus.SUCCESS);
        } else if (merchantStatusProperties.isFail(event.getStatus())) {
            updateStatus(id.toString(), OrderStatus.TIMEOUT);
        } else {
            merchantUnknownStatusService.sendUnknownStatusCallback(event);
        }
        orderRepository.updateMerchantOrderStatusById(id, event.getStatus());
    }

    /**
     * Выполняет поиск order по заданной спецификации критериев с поддержкой пагинации и сортировки.
     *
     * @param spec    динамическая спецификация критериев фильтрации
     * @param page    номер запрашиваемой страницы
     * @param size    максимальное количество order на странице
     * @param sorters список строк правил сортировки (например, {@code "amount,desc"})
     * @return {@link Page} с найденными и преобразованными в DTO order
     */
    @Transactional(readOnly = true)
    public Page<OrderDTO> findOrders(Specification<Order> spec, int page, int size, List<String> sorters) {
        Pageable pageable = PageableUtils.createPageable(page, size, sorters);
        return orderRepository.findAll(spec, pageable).map(orderMapper::entityToDTO);
    }

    /**
     * Находит order по идентификатору ордера в системе мерчанта.
     *
     * @param id идентификатор order (UUID)
     * @return {@link Optional} с DTO order, или пустой {@link Optional}, если order не найден
     */
    @Transactional(readOnly = true)
    public Optional<OrderDTO> findByMerchantOrderId(String id) {
        return orderRepository.findByMerchantOrderId(id).map(orderMapper::entityToDTO);
    }

    /**
     * Выполняет поиск всех order, соответствующих заданной спецификации критериев, без пагинации.
     * <p>
     *
     * @param spec динамическая спецификация критериев фильтрации
     * @return {@link List} со всеми найденными сущностями order
     */
    @Transactional(readOnly = true)
    public List<Order> findOrderByField(Specification<Order> spec) {
        return orderRepository.findAll(spec);
    }

}
