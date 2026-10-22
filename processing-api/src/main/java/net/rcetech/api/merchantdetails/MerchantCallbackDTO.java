package net.rcetech.api.merchantdetails;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tgb.cryptoexchange.commons.enums.Merchant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantCallbackDTO {

    private String merchantOrderId;

    private String status;

    private String statusDescription;

    private Merchant merchant;
}

