package net.rcetech.meta.orders;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import tgb.cryptoexchange.commons.enums.Merchant;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.deser.std.StdDeserializer;

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

    public static class MerchantDeserializer extends StdDeserializer<Merchant> {

        public MerchantDeserializer(Class<?> vc) {
            super(vc);
        }

        @Override
        public Merchant deserialize(JsonParser p, DeserializationContext ctxt) {
            try {
                return Merchant.valueOf(p.getText());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

    }

}
