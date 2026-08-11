package net.rcetech.orders.kafka;

import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Deserializer;
import net.rcetech.orders.exceptions.BaseException;

import java.nio.charset.StandardCharsets;

@Slf4j
public class MerchantCallbackEventDeserializer implements Deserializer<MerchantCallbackEvent> {

    private final ObjectMapper objectMapper;

    public MerchantCallbackEventDeserializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public MerchantCallbackEvent deserialize(String topic, byte[] data) {
        try {
            if (data == null)
                return null;
            return objectMapper.readValue(data, MerchantCallbackEvent.class);
        } catch (Exception e) {
            throw new BaseException(
                    "Error occurred while deserializer value: " + new String(data, StandardCharsets.UTF_8));
        }
    }

}
