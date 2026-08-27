package net.rcetech.meta.clients;

import net.rcetech.meta.DictionaryField;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class ClientStatusDictionaryField implements DictionaryField {

    @Override
    public String getField() {
        return "ClientStatus";
    }

    @Override
    public List<Map<String, Object>> getContent() {
        return Arrays.stream(ClientStatus.values())
                .map(clientStatus -> Map.<String, Object>of(
                        "name", clientStatus.name(),
                        "description", clientStatus.getDescription())
                ).toList();
    }
}
