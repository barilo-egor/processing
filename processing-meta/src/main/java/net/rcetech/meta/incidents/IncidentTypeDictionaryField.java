package net.rcetech.meta.incidents;

import net.rcetech.meta.DictionaryField;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class IncidentTypeDictionaryField implements DictionaryField {

    @Override
    public String getField() {
        return "IncidentType";
    }

    @Override
    public List<Map<String, Object>> getContent() {
        return Arrays.stream(IncidentType.values())
                .map(type -> Map.<String, Object>of(
                        "name", type.name(),
                        "summary", type.getSummary(),
                        "description", type.getDescription())
                ).toList();
    }
}
