package net.rcetech.api.mapper;

import net.rcetech.api.dto.ApiDetailsResponse;
import net.rcetech.api.dto.CreateOrderRequest;
import net.rcetech.grpc.generated.DetailsRequestGrpc;
import net.rcetech.grpc.generated.DetailsResponseGrpc;
import net.rcetech.meta.orders.RequestMethod;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.UUID;

@Mapper(
        componentModel = "spring",
        collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR
)
public interface DetailsMapper {

    ApiDetailsResponse grpcResponseToDTO(DetailsResponseGrpc response);

    @Mapping(target = "requestId", source = "requestId")
    @Mapping(target = "internalId", source = "orderId")
    @Mapping(target = "userId", source = "orderDTO.userId")
    @Mapping(target = "amount", source = "orderDTO.amount")
    @Mapping(target = "requestMethodList", source = "orderDTO.methods")
    DetailsRequestGrpc detailsRequestDTOToGrpc(UUID requestId, UUID orderId, CreateOrderRequest orderDTO);

    default String mapRequestMethodToString(RequestMethod method) {
        return method != null ? method.name() : null;
    }

    default String mapUuidToString(UUID uuid) {
        return uuid != null ? uuid.toString() : null;
    }

    default UUID mapStringToUuid(String uuid) {
        return uuid != null ? UUID.fromString(uuid) : null;
    }

    default Merchant mapStringToMerchant(String merchant) {
        return merchant != null ? Merchant.valueOf(merchant) : null;
    }
}
