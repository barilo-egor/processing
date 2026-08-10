package net.rcetech.api.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import net.rcetech.api.constants.Metrics;
import net.rcetech.api.dto.*;
import net.rcetech.api.exceptions.OrderNotFoundException;
import net.rcetech.api.mapper.DetailsMapper;
import net.rcetech.api.mapper.OrdersMapper;
import net.rcetech.meta.orders.dto.*;
import net.rcetech.api.dto.OrderResponseDTO;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import net.rcetech.orders.service.OrderApi;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static net.rcetech.api.constants.Metrics.CLIENT_ID;

@Service
@Slf4j
public class OrderService {

    private final ApiMerchantDetailsGrpcService detailsGrpcService;

    private final DetailsMapper detailsMapper;

    private final OrdersMapper ordersMapper;

    private final OrderApi orderApi;

    private final MeterRegistry meterRegistry;

    public OrderService(ApiMerchantDetailsGrpcService detailsGrpcService, DetailsMapper detailsMapper,
            OrdersMapper ordersMapper, OrderApi orderApi, MeterRegistry meterRegistry) {
        this.detailsGrpcService = detailsGrpcService;
        this.detailsMapper = detailsMapper;
        this.ordersMapper = ordersMapper;
        this.orderApi = orderApi;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Выполняет сквозной процесс создания заказа на основе gRPC-сервисов.
     * <p>
     * Метод последовательно запрашивает платежные реквизиты в {@code merchant-details},
     * регистрирует новый заказ в {@code api-orders} и рассчитывает срок его жизни.
     *
     * @param clientRequest      параметры заказа от клиента.
     * @param client             данные авторизованного клиента.
     * @param clientOrderTimeout таймаут действия заказа в секундах.
     * @return {@link OrderResponseDTO} с полными деталями и статусом созданного заказа.
     */
    public OrderResponseDTO createOrder(CreateOrderDTO clientRequest, ClientByApiKeyDTO client,
            Integer clientOrderTimeout) {
        ApiDetailsRequestDTO apiDetailsRequestDTO = detailsMapper.orderToRequestDTO(clientRequest);
        UUID orderId = apiDetailsRequestDTO.getInternalId();

        Timer.Sample sample = Timer.start(meterRegistry);
        ApiDetailsResponseDTO detailsResponseDTO = detailsGrpcService.getDetails(apiDetailsRequestDTO, client);
        sample.stop(meterRegistry.timer(Metrics.DETAILS_REQUEST, CLIENT_ID, String.valueOf(client.getClientId())));
        log.debug("Для клиентского запроса {} найдены реквизиты в merchant-details {}", clientRequest,
                detailsResponseDTO);

        CreateOrderRequestDTO requestOrder = ordersMapper.createRequestDTO(orderId, clientRequest,
                detailsResponseDTO, client);
        net.rcetech.meta.orders.dto.OrderResponseDTO orderResponseDTO = orderApi.createOrder(requestOrder);

        log.debug("Для клиентского запроса {} создан order в api-orders {}", clientRequest, orderResponseDTO);

        Instant expiresAt = orderResponseDTO.createdAt().plusSeconds(clientOrderTimeout);
        OrderResponseDTO responseDTO = OrderResponseDTO.builder()
                .id(orderId)
                .internalId(clientRequest.getInternalId())
                .details(detailsResponseDTO.getDetails())
                .status(orderResponseDTO.status())
                .createdAt(orderResponseDTO.createdAt())
                .expiresAt(expiresAt)
                .build();
        log.debug("Для клиентского запроса {} сформирован ответ {}", clientRequest, responseDTO);
        return responseDTO;
    }

    /**
     * Формирует тестовый заказ с захардкоженными платежными реквизитами.
     * <p>
     * Используется в режиме отладки при передаче флага тестового заказа,
     * полностью изолируя выполнение от внешних gRPC-микросервисов.
     *
     * @param clientRequest      параметры запроса от клиента.
     * @param clientOrderTimeout таймаут действия заказа в секундах для расчета экспирации.
     * @return {@link OrderResponseDTO} со статусом NEW и фиксированными реквизитами карты.
     */
    public OrderResponseDTO testOrder(CreateOrderDTO clientRequest, Integer clientOrderTimeout) {
        Instant createdAt = Instant.now();
        Instant expiresAt = createdAt.plusSeconds(clientOrderTimeout);
        return OrderResponseDTO.builder()
                .id(UUID.randomUUID())
                .internalId(clientRequest.getInternalId())
                .details(DetailsDTO.builder()
                        .requestMethod("CARD")
                        .details("1111 2222 3333 4444")
                        .bank("ALFA")
                        .build())
                .status("NEW")
                .createdAt(createdAt)
                .expiresAt(expiresAt)
                .build();
    }

    /**
     * Находит заказ по его идентификатору и рассчитывает срок его жизни.
     *
     * @param id                 системный или внешний идентификатор заказа.
     * @param clientOrderTimeout таймаут действия заказа в секундах для расчета экспирации.
     * @param client             данные авторизованного клиента.
     * @return {@link OrderResponseDTO} с деталями и актуальным статусом найденного заказа.
     */
    public OrderResponseDTO findOrder(String id, Integer clientOrderTimeout, ClientByApiKeyDTO client) {
        GetOrdersFilterDTO filter = GetOrdersFilterDTO.builder()
                .pagination(new PaginationParamsDTO(0, 1, List.of()))
                .id(UUID.fromString(id))
                .clientIds(List.of(client.getClientId()))
                .build();

        OrdersPageResponseDTO pageResponse = orderApi.getOrders(filter);
        net.rcetech.meta.orders.dto.OrderResponseDTO order = pageResponse.orders().stream()
                .findFirst()
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + id));

        Instant createdAt = order.createdAt();
        Instant expiresAt = createdAt.plusSeconds(clientOrderTimeout);

        return OrderResponseDTO.builder()
                .id(order.id())
                .internalId(order.internalId())
                //                у ордера их нет, не описано до конца
                //                .details(orderDTO.get)
                .status(order.status())
                .createdAt(createdAt)
                .expiresAt(expiresAt)
                .build();
    }

    /**
     * Получает список заказов клиента с учетом пагинации и рассчитывает их экспирацию.
     *
     * @param clientOrderTimeout таймаут действия заказа в секундах для расчета экспирации.
     * @param client             данные авторизованного клиента для фильтрации.
     * @param pageable           параметры пагинации и сортировки.
     * @return список {@link OrderResponseDTO} с актуальными статусами и временем жизни.
     */
    public List<OrderResponseDTO> findOrders(Integer clientOrderTimeout, ClientByApiKeyDTO client, Pageable pageable) {
        List<String> sorters = pageable.getSort().stream()
                .map(order -> order.getProperty() + "," + order.getDirection().name().toLowerCase())
                .toList();

        GetOrdersFilterDTO filter = GetOrdersFilterDTO.builder()
                .pagination(new PaginationParamsDTO(pageable.getPageNumber(), pageable.getPageSize(), sorters))
                .clientIds(List.of(client.getClientId()))
                .build();

        OrdersPageResponseDTO pageResponse = orderApi.getOrders(filter);

        return pageResponse.orders().stream().map(dto -> {
            Instant createdAt = dto.createdAt();
            Instant expiresAt = createdAt != null ? createdAt.plusSeconds(clientOrderTimeout) : null;

            return OrderResponseDTO.builder()
                    .id(dto.id())
                    .internalId(dto.internalId())
                    //                у ордера их нет, не описано до конца
                    //                .details(orderDTO.get)
                    .status(dto.status())
                    .createdAt(createdAt)
                    .expiresAt(expiresAt)
                    .build();
        }).toList();
    }

    /**
     * Отменяет заказ через gRPC и возвращает его обновленные детали.
     *
     * @param id                 идентификатор отменяемого заказа.
     * @param clientOrderTimeout таймаут действия заказа в секундах для расчета экспирации.
     * @param client             данные авторизованного клиента.
     * @return {@link OrderResponseDTO} с актуальным статусом CANCELED.
     */
    public OrderResponseDTO cancelOrder(String id, Integer clientOrderTimeout, ClientByApiKeyDTO client) {
        UpdateOrderStatusRequestDTO request = new UpdateOrderStatusRequestDTO(
                UUID.fromString(id),
                "CANCELED",
                client.getClientId()
        );

        orderApi.updateOrderStatus(request);

        return findOrder(id, clientOrderTimeout, client);
    }

}
