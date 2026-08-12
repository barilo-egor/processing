package net.rcetech.meta.orders.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import net.rcetech.meta.orders.OrderStatus;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Instant;
import java.util.UUID;

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
