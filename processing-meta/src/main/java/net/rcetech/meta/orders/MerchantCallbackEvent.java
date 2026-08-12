package net.rcetech.meta.orders;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.*;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.io.IOException;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class MerchantCallbackEvent {

    private String merchantOrderId;

    private String status;

    private String statusDescription;

    @JsonDeserialize(using = MerchantDeserializer.class)
    private Merchant merchant;

    public static class MerchantDeserializer extends JsonDeserializer<Merchant> {

        @Override
        public Merchant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            try {
                return Merchant.valueOf(p.getText());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

    }

}
