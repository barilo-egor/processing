package net.rcetech.meta.orders;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import tgb.cryptoexchange.commons.enums.Merchant;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MerchantCallbackEvent {

    private String merchantOrderId;

    private String status;

    private String statusDescription;

    @JsonDeserialize(using = MerchantDeserializer.class)
    private Merchant merchant;

    public static class MerchantDeserializer extends ValueDeserializer<Merchant> {

        @Override
        public Merchant deserialize(JsonParser p, DeserializationContext ctxt) {
            try {
                return Merchant.valueOf(p.getValueAsString());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

    }

}
