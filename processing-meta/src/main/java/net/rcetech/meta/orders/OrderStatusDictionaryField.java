package net.rcetech.meta.orders;

import net.rcetech.meta.DictionaryField;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class OrderStatusDictionaryField implements DictionaryField {
    @Override
    public String getField() {
        return "OderStatus";
    }

    @Override
    public List<Map<String, Object>> getContent() {
        return Arrays.stream(OrderStatus.values())
                .map(orderStatus -> Map.<String, Object>of(
                        "name", orderStatus.name(),
                        "description", orderStatus.getDescription())
                ).toList();
    }
}
