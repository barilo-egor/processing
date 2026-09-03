package net.rcetech.api.mapper;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.api.dto.ApiDetailsResponseDTO;
import net.rcetech.api.dto.ClientByApiKeyDTO;
import net.rcetech.api.dto.CreateOrderDTO;
import net.rcetech.api.exceptions.EnableUniqueAmountException;
import net.rcetech.meta.orders.dto.CreateOrderRequestDTO;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Slf4j
@Component
public class OrdersMapper {

    public CreateOrderRequestDTO createRequestDTO(UUID orderId, CreateOrderDTO clientRequest,
            ApiDetailsResponseDTO detailsResponseDTO, ClientByApiKeyDTO client) {
        Integer amount;
        if (Boolean.TRUE.equals(clientRequest.enableUniqueAmount())) {
            amount = Objects.isNull(detailsResponseDTO.getAmount()) ?
                    clientRequest.amount() :
                    detailsResponseDTO.getAmount();
        } else {
            if (Objects.nonNull(detailsResponseDTO.getAmount())) {
                throw new EnableUniqueAmountException();
            }
            amount = clientRequest.amount();
        }

        return new CreateOrderRequestDTO(
                orderId,
                client.getClientId(),
                clientRequest.internalId(),
                detailsResponseDTO.getMerchant(),
                detailsResponseDTO.getOrderId(),
                detailsResponseDTO.getOrderStatus(),
                amount,
                Boolean.TRUE.equals(clientRequest.enableUniqueAmount()),
                clientRequest.callbackUrl()
        );
    }

}
