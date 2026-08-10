package net.rcetech.details.dto;

import lombok.Builder;
import lombok.Data;
import net.rcetech.details.enums.RequestMethod;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
public class ApiDetailsRequestDTO {

    private UUID requestId;

    private UUID internalId;

    private String userId;

    private Integer amount;

    private Set<RequestMethod> methods;

}
