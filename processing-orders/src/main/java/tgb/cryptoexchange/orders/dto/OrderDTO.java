package tgb.cryptoexchange.orders.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import tgb.cryptoexchange.commons.enums.Merchant;
import tgb.cryptoexchange.orders.enums.OrderStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * @see tgb.cryptoexchange.orders.entity.Order
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderDTO {

    private UUID id;

    private Long clientId;

    private String internalId;

    private Merchant merchant;

    private String merchantOrderId;

    private String merchantOrderStatus;

    private OrderStatus status;

    private Integer amount;

    private Boolean enableUniqueAmount;

    private String callbackUrl;

    private Instant createdAt;

}
