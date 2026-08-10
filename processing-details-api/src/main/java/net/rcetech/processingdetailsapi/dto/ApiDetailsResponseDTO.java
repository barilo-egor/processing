package net.rcetech.processingdetailsapi.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApiDetailsResponseDTO {

    private String requestId;

    private String orderId;

    private String orderStatus;

    private String merchant;

    private Integer amount;

    private DetailsDTO details;

}
