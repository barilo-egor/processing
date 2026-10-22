package net.rcetech.meta.billing;

import net.rcetech.meta.DictionaryField;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class OperationDictionaryField implements DictionaryField {

    @Override
    public String getField() {
        return "Operation";
    }

    @Override
    public List<Map<String, Object>> getContent() {
        return Arrays.stream(Operation.values())
                .map(operation -> Map.<String, Object>of(
                        "name", operation.name(),
                        "description", operation.getDescription())
                ).toList();
    }
}
