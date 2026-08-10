package net.rcetech.processingdetailsapi.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import net.rcetech.processingdetailsapi.dto.*;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import net.rcetech.processingdetailsapi.constants.Metrics;
import tgb.cryptoexchange.processingdetailsapi.dto.*;
import net.rcetech.processingdetailsapi.mapper.DetailsMapper;
import net.rcetech.processingdetailsapi.mapper.OrdersMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static net.rcetech.processingdetailsapi.constants.Metrics.CLIENT_ID;

@Service
@Slf4j
public class OrderService {

    private final ApiMerchantDetailsGrpcService detailsGrpcService;

    private final DetailsMapper detailsMapper;

    private final OrdersMapper ordersMapper;

    private final ApiOrdersGrpcService apiOrdersGrpcService;

    private final MeterRegistry meterRegistry;

    public OrderService(ApiMerchantDetailsGrpcService detailsGrpcService, DetailsMapper detailsMapper,
            OrdersMapper ordersMapper, ApiOrdersGrpcService apiOrdersGrpcService, MeterRegistry meterRegistry) {
        this.detailsGrpcService = detailsGrpcService;
        this.detailsMapper = detailsMapper;
        this.ordersMapper = ordersMapper;
        this.apiOrdersGrpcService = apiOrdersGrpcService;
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

        ApiOrdersCreateRequestDTO requestOrder = ordersMapper.createRequestDTO(orderId, clientRequest,
                detailsResponseDTO, client);
        ApiOrdersResponseDTO orderResponseDTO = apiOrdersGrpcService.createOrder(requestOrder);
        log.debug("Для клиентского запроса {} создан order в api-orders {}", clientRequest, orderResponseDTO);

        Instant expiresAt = orderResponseDTO.getCreatedAt().plusSeconds(clientOrderTimeout);
        OrderResponseDTO responseDTO = OrderResponseDTO.builder()
                .id(orderId)
                .internalId(clientRequest.getInternalId())
                .details(detailsResponseDTO.getDetails())
                .status(orderResponseDTO.getStatus())
                .createdAt(orderResponseDTO.getCreatedAt())
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
        ApiOrdersResponseDTO orderDTO = apiOrdersGrpcService.getOrders(id, client.getClientId());
        Instant createdAt = orderDTO.getCreatedAt();
        Instant expiresAt = createdAt.plusSeconds(clientOrderTimeout);
        return OrderResponseDTO.builder()
                .id(orderDTO.getId())
                .internalId(orderDTO.getInternalId())
                //                у ордера их нет, не описано до конца
                //                .details(orderDTO.get)
                .status(orderDTO.getStatus())
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
        List<ApiOrdersResponseDTO> orderDTO = apiOrdersGrpcService.findOrders(client.getClientId(), pageable);
        return orderDTO.stream().map(dto -> {
            Instant createdAt = dto.getCreatedAt();
            Instant expiresAt = createdAt.plusSeconds(clientOrderTimeout);
            return OrderResponseDTO.builder()
                    .id(dto.getId())
                    .internalId(dto.getInternalId())
                    //                у ордера их нет, не описано до конца
                    //                .details(orderDTO.get)
                    .status(dto.getStatus())
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
        apiOrdersGrpcService.cancelOrder(id, client.getClientId());
        return findOrder(id, clientOrderTimeout, client);
    }

}
