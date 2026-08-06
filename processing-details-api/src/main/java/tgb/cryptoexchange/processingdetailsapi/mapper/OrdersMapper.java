package tgb.cryptoexchange.processingdetailsapi.mapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.grpc.generated.*;
import tgb.cryptoexchange.processingdetailsapi.dto.*;
import tgb.cryptoexchange.processingdetailsapi.exceptions.EnableUniqueAmountException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Component
public class OrdersMapper {

    public ApiOrdersCreateRequestDTO createRequestDTO(UUID orderId, CreateOrderDTO clientRequest,
            ApiDetailsResponseDTO detailsResponseDTO, ClientByApiKeyDTO client) {
        Integer amount;
        if (clientRequest.isEnableUniqueAmount()) {
            amount = Objects.isNull(detailsResponseDTO.getAmount()) ?
                    clientRequest.getAmount() :
                    detailsResponseDTO.getAmount();
        } else {
            if (Objects.nonNull(detailsResponseDTO.getAmount())) {
                throw new EnableUniqueAmountException();
            }
            amount = clientRequest.getAmount();
        }

        return ApiOrdersCreateRequestDTO.builder()
                .id(orderId)
                .clientId(client.getClientId())
                .internalId(clientRequest.getInternalId())
                .merchant(detailsResponseDTO.getMerchant())
                .merchantOrderId(detailsResponseDTO.getOrderId())
                .merchantOrderStatus(detailsResponseDTO.getOrderStatus())
                .amount(amount)
                .enableUniqueAmount(clientRequest.isEnableUniqueAmount())
                .callbackUrl(clientRequest.getCallbackUrl())
                .build();
    }

    public CreateOrderGrpc createOrderGrpc(ApiOrdersCreateRequestDTO createRequestDTO) {
        CreateOrderGrpc.Builder builder = CreateOrderGrpc.newBuilder()
                .setClientId(createRequestDTO.getClientId())
                .setInternalId(createRequestDTO.getInternalId())
                .setMerchant(createRequestDTO.getMerchant())
                .setMerchantOrderId(createRequestDTO.getMerchantOrderId())
                .setMerchantOrderStatus(createRequestDTO.getMerchantOrderStatus())
                .setAmount(createRequestDTO.getAmount())
                .setEnableUniqueAmount(createRequestDTO.isEnableUniqueAmount());
        if (createRequestDTO.getCallbackUrl() != null) {
            builder.setCallbackUrl(createRequestDTO.getCallbackUrl());
        }
        return builder.build();
    }

    public ApiOrdersResponseDTO grpcResponseToDTO(CreateOrderResponseGrpc response) {
        return ApiOrdersResponseDTO.builder()
                .id(UUID.fromString(response.getId()))
                .clientId(response.getClientId())
                .internalId(response.getInternalId())
                .status(response.getStatus())
                .amount(response.getAmount())
                .enableUniqueAmount(response.getEnableUniqueAmount())
                .callbackUrl(response.getCallbackUrl())
                .createdAt(response.hasCreatedAt() ? Instant.ofEpochSecond(
                        response.getCreatedAt().getSeconds(),
                        response.getCreatedAt().getNanos()
                ) : null)
                .build();
    }

    /**
     * @param id (Поиск по идентификатору системы)
     */
    public GetOrdersGrpc getOrdersByIdGrpc(String id, Long clientId) {
        GetOrdersGrpc.Builder builder = GetOrdersGrpc.newBuilder();
        builder.setPagination(PaginationParams.newBuilder()
                .setPage(0)
                .setSize(1)
                .build());

        builder.setId(id);
        builder.addClientIds(clientId);
        return builder.build();
    }

    /**
     * Поиск всех ордеров клиента
     */
    public GetOrdersGrpc getOrdersGrpc(Long clientId, Pageable pageable) {
        GetOrdersGrpc.Builder builder = GetOrdersGrpc.newBuilder();
        List<String> grpcSorters = pageable.getSort().stream()
                .map(order -> order.getProperty() + "," + order.getDirection().name().toLowerCase())
                .toList();
        builder.setPagination(PaginationParams.newBuilder()
                .setPage(pageable.getPageNumber())
                .setSize(pageable.getPageSize())
                .addAllSorters(grpcSorters)
                .build());
        builder.addClientIds(clientId);
        return builder.build();
    }

    /**
     * @param id (Поиск по идентификатору сторонней системы)
     */
    public GetOrdersGrpc getOrdersByExternalIdGrpc(String id, Long clientId) {
        GetOrdersGrpc.Builder builder = GetOrdersGrpc.newBuilder();
        builder.setPagination(PaginationParams.newBuilder()
                .setPage(0)
                .setSize(1)
                .build());
        builder.setInternalId(id);
        builder.addClientIds(clientId);

        return builder.build();
    }

    public ApiOrdersResponseDTO getOrder(OrderResponse orderResponse) {
        return ApiOrdersResponseDTO.builder()
                .id(UUID.fromString(orderResponse.getId()))
                .clientId(orderResponse.getClientId())
                .internalId(orderResponse.getInternalId())
                .status(orderResponse.getStatus())
                .amount(orderResponse.getAmount())
                .enableUniqueAmount(orderResponse.getEnableUniqueAmount())
                .callbackUrl(orderResponse.getCallbackUrl())
                .createdAt(Instant.ofEpochSecond(orderResponse.getCreatedAt().getSeconds(),
                        orderResponse.getCreatedAt().getNanos()))
                .build();
    }

}
