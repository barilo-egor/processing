package net.rcetech.meta.billing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import net.rcetech.meta.billing.Operation;
import net.rcetech.meta.billing.TransactionType;

import java.time.Instant;
import java.util.UUID;

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
