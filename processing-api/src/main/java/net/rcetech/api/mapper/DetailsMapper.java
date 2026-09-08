package net.rcetech.api.mapper;

import net.rcetech.api.dto.ApiDetailsResponse;
import net.rcetech.api.dto.CreateOrderRequest;
import net.rcetech.grpc.generated.DetailsRequestGrpc;
import net.rcetech.grpc.generated.DetailsResponseGrpc;
import net.rcetech.meta.orders.RequestMethod;
import org.mapstruct.*;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.List;
import java.util.Set;
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
    @Mapping(target = "requestMethod", source = "orderDTO.methods", qualifiedByName = "mapMethods")
    DetailsRequestGrpc detailsRequestDTOToGrpc(UUID requestId, UUID orderId, CreateOrderRequest orderDTO);

    @Named("mapMethods")
    default List<String> mapMethods(Set<RequestMethod> methods) {
        if (methods == null) {
            return List.of();
        }
        return methods.stream()
                .map(RequestMethod::name)
                .toList();
    }

    default String mapUuidToString(UUID uuid) {
        return uuid != null ? uuid.toString() : null;
    }

    default Merchant mapStringToMerchant(String merchant) {
        return merchant != null ? Merchant.valueOf(merchant) : null;
    }
}
