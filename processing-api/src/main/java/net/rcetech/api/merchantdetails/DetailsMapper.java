package net.rcetech.api.merchantdetails;

import net.rcetech.grpc.generated.CancelOrderRequestGrpc;
import net.rcetech.grpc.generated.DetailsResponseGrpc;
import net.rcetech.grpc.generated.MerchantCallbackGrpc;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
        componentModel = "spring",
        collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR
)
public interface DetailsMapper {

    ApiDetailsResponse grpcResponseToDTO(DetailsResponseGrpc response);

    @Mapping(target = "orderId", source = "merchantOrderId")
    @Mapping(target = "method", source = "method")
    CancelOrderRequestGrpc cancelOrderDTOToGrpc(CancelOrderDTO cancelOrderDTO);

    MerchantCallbackGrpc merchantCallbackDTOToGrpc(MerchantCallbackDTO merchantCallbackDTO);
}
