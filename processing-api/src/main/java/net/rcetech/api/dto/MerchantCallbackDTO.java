package net.rcetech.api.dto;

import lombok.Builder;
import lombok.Data;
import tgb.cryptoexchange.commons.enums.Merchant;

@Data
@Builder
public class MerchantCallbackDTO {

    private String merchantOrderId;

    private String status;

    private String statusDescription;

    private Merchant merchant;
}

