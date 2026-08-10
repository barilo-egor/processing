package net.rcetech.orders.kafka;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.rcetech.orders.enums.Operation;
import net.rcetech.orders.enums.TransactionType;

import java.util.Objects;
import java.util.UUID;

/**
 * DTO для отправки события подтверждения заказа в Kafka.
 */
@Data
@NoArgsConstructor
public class OrderConfirmationEvent {

    private UUID id;

    private Long clientId;

    private Integer amount;

    private Operation operation;

    private TransactionType type;

    private String comment;

    public OrderConfirmationEvent(UUID id, Long clientId, Integer amount, Operation operation, TransactionType type,
            UUID orderId) {
        this.id = id;
        this.clientId = clientId;
        this.amount = amount;
        this.operation = operation;
        this.type = type;
        setComment(orderId);
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public OrderConfirmationEvent(
            @JsonProperty("id") UUID id,
            @JsonProperty("clientId") Long clientId,
            @JsonProperty("amount") Integer amount,
            @JsonProperty("operation") Operation operation,
            @JsonProperty("type") TransactionType type,
            @JsonProperty("comment") String comment) {
        this.id = id;
        this.clientId = clientId;
        this.amount = amount;
        this.operation = operation;
        this.type = type;
        this.comment = comment;
    }

    public void setComment(UUID orderId) {
        this.comment = "Зачисление по подтвержденному ордеру";
        if (Objects.nonNull(orderId)) {
            this.comment = this.comment + " " + orderId;
        }
    }

}
