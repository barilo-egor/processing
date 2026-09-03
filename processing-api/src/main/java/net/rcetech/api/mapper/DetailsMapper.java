package net.rcetech.api.mapper;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import net.rcetech.api.dto.ApiDetailsRequestDTO;
import net.rcetech.api.dto.ApiDetailsResponseDTO;
import net.rcetech.api.dto.CreateOrderDTO;
import net.rcetech.api.dto.DetailsDTO;
import net.rcetech.api.enums.RequestMethod;
import net.rcetech.grpc.generated.DetailsRequestGrpc;
import net.rcetech.grpc.generated.DetailsResponseGrpc;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DetailsMapper {

    private final TimeBasedEpochGenerator generator = Generators.timeBasedEpochGenerator();

    public ApiDetailsResponseDTO grpcResponseToDTO(DetailsResponseGrpc response) {
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

    public DetailsRequestGrpc detailsRequestDTOToGrpc(ApiDetailsRequestDTO requestDTO) {
        List<String> methodsList = requestDTO.getMethods() != null
                ? requestDTO.getMethods().stream()
                .map(RequestMethod::name)
                .toList() : List.of();
        return DetailsRequestGrpc.newBuilder()
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
