package net.rcetech.processingdetailsapi.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class OrderResponseDTO {

    private UUID id;

    private String internalId;

    private DetailsDTO details;

    private String status;

    private Instant createdAt;

    private Instant expiresAt;

}
