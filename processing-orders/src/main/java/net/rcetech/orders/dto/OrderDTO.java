package net.rcetech.orders.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import net.rcetech.orders.entity.Order;
import net.rcetech.commons.enums.Merchant;
import net.rcetech.orders.enums.OrderStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * @see Order
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
