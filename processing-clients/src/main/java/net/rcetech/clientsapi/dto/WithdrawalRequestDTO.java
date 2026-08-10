package net.rcetech.clientsapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.rcetech.clientsapi.entity.WithdrawalRequest;
import net.rcetech.clientsapi.enums.WithdrawalRequestStatus;

import java.time.Instant;

/**
 * @see WithdrawalRequest
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalRequestDTO {

    private Long id;

    private Long clientId;

    private Integer amount;

    private Instant createdAt;

    private WithdrawalRequestStatus status;

    private String wallet;

    private String comment;

}
