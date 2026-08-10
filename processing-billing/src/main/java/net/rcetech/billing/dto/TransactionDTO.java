package net.rcetech.billing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import net.rcetech.billing.entity.Transaction;
import net.rcetech.billing.enums.Operation;
import net.rcetech.billing.enums.TransactionType;

import java.time.Instant;
import java.util.UUID;

/**
 * @see Transaction
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionDTO {

    @NotNull
    private UUID id;

    @NotNull
    private Long clientId;

    @NotNull
    private Integer amount;

    @NotNull
    private Operation operation;

    @NotNull
    private TransactionType type;

    private String comment;

    private Instant createdAt;

}
