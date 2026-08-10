package net.rcetech.processingdetailsapi.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class DetailsDTO {

    private String requestMethod;

    private String details;

    private String bank;

    private String operator;

}
