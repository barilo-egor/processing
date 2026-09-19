package net.rcetech.meta.billing;

import net.rcetech.meta.DictionaryField;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class TransactionTypeDictionaryField implements DictionaryField {

    @Override
    public String getField() {
        return "TransactionType";
    }

    @Override
    public List<Map<String, Object>> getContent() {
        return Arrays.stream(TransactionType.values())
                .map(transactionType -> Map.<String, Object>of(
                        "name", transactionType.name(),
                        "description", transactionType.getDescription())
                ).toList();
    }
}
