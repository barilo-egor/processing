package net.rcetech.meta.incidents;

import net.rcetech.meta.DictionaryField;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class IncidentStatusDictionaryField implements DictionaryField {

    @Override
    public String getField() {
        return "IncidentStatus";
    }

    @Override
    public List<Map<String, Object>> getContent() {
        return Arrays.stream(IncidentStatus.values())
                .map(status -> Map.<String, Object>of(
                        "name", status.name(),
                        "description", status.getDescription())
                ).toList();
    }
}
