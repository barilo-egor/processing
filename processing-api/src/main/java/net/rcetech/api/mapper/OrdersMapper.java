package net.rcetech.api.mapper;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.api.dto.ApiDetailsResponseDTO;
import net.rcetech.api.dto.ClientByApiKeyDTO;
import net.rcetech.api.dto.CreateOrderDTO;
import net.rcetech.api.exceptions.EnableUniqueAmountException;
import org.springframework.stereotype.Component;
import rce.tech.ordersapi.dto.CreateOrderRequestDTO;

import java.util.Objects;
import java.util.UUID;

@Slf4j
@Component
public class OrdersMapper {

    public CreateOrderRequestDTO createRequestDTO(UUID orderId, CreateOrderDTO clientRequest,
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

        return new CreateOrderRequestDTO(
                orderId,
                client.getClientId(),
                clientRequest.getInternalId(),
                detailsResponseDTO.getMerchant(),
                detailsResponseDTO.getOrderId(),
                detailsResponseDTO.getOrderStatus(),
                amount,
                clientRequest.isEnableUniqueAmount(),
                clientRequest.getCallbackUrl()
        );
    }

}
