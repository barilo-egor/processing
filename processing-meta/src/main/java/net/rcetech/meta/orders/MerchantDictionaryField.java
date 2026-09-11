package net.rcetech.meta.orders;

import net.rcetech.meta.DictionaryField;
import org.springframework.stereotype.Component;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class MerchantDictionaryField implements DictionaryField {

    @Override
    public String getField() {
        return "Merchant";
    }

    @Override
    public List<Map<String, Object>> getContent() {
        return Arrays.stream(Merchant.values())
                .map(clientStatus -> Map.<String, Object>of(
                        "name", clientStatus.name(),
                        "displayName", clientStatus.getDisplayName())
                ).toList();
    }
}
