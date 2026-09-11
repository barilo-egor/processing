package net.rcetech.meta.orders;

import net.rcetech.meta.DictionaryField;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class RequestMethodDictionaryField implements DictionaryField {
    @Override
    public String getField() {
        return "";
    }

    @Override
    public List<Map<String, Object>> getContent() {
        return Arrays.stream(RequestMethod.values())
                .map(requestMethod -> Map.<String, Object>of(
                        "name", requestMethod.name(),
                        "description", requestMethod.getDescription())
                ).toList();
    }
}
