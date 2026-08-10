package net.rcetech.details.mapper;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.grpc.generated.GetDetailsGrpc;
import tgb.cryptoexchange.grpc.generated.GetDetailsResponseGrpc;
import net.rcetech.details.dto.ApiDetailsRequestDTO;
import net.rcetech.details.dto.ApiDetailsResponseDTO;
import net.rcetech.details.dto.CreateOrderDTO;
import net.rcetech.details.dto.DetailsDTO;
import net.rcetech.details.enums.RequestMethod;

import java.util.List;

@Component
public class DetailsMapper {

    private final TimeBasedEpochGenerator generator = Generators.timeBasedEpochGenerator();

    public ApiDetailsResponseDTO grpcResponseToDTO(GetDetailsResponseGrpc response) {
        return ApiDetailsResponseDTO.builder()
                .requestId(response.getRequestId())
                .orderId(response.getOrderId())
                .orderStatus(response.getOrderStatus())
                .merchant(response.getMerchant())
                .amount(response.getAmount())
                .details(DetailsDTO.builder()
                        .requestMethod(response.getDetails().getRequestMethod())
                        .bank(response.getDetails().getBank())
                        .operator(response.getDetails().getOperator())
                        .details(response.getDetails().getDetails())
                        .build())
                .build();
    }

    public GetDetailsGrpc detailsRequestDTOToGrpc(ApiDetailsRequestDTO requestDTO) {
        List<String> methodsList = requestDTO.getMethods() != null
                ? requestDTO.getMethods().stream()
                .map(RequestMethod::name)
                .toList() : List.of();
        return GetDetailsGrpc.newBuilder()
                .setRequestId(requestDTO.getRequestId().toString())
                .setInternalId(requestDTO.getInternalId().toString())
                .setUserId(requestDTO.getUserId())
                .setAmount(requestDTO.getAmount())
                .addAllRequestMethod(methodsList)
                .build();
    }

    public ApiDetailsRequestDTO orderToRequestDTO(CreateOrderDTO orderDTO) {
        return ApiDetailsRequestDTO.builder()
                .userId(orderDTO.getUserId())
                .amount(orderDTO.getAmount())
                .methods(orderDTO.getMethods())
                .requestId(generator.generate())
                .internalId(generator.generate())
                .build();
    }

}
