package net.rcetech.orders.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serializer;
import net.rcetech.orders.exceptions.BodyMappingException;

@Slf4j
public class UnknownStatusSerializer implements Serializer<MerchantCallbackEvent> {

    private final ObjectMapper objectMapper;

    public UnknownStatusSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public byte[] serialize(String topic, MerchantCallbackEvent merchantCallbackEvent) {
        try {
            if (merchantCallbackEvent == null) {
                return new byte[0];
            }
            return objectMapper.writeValueAsBytes(merchantCallbackEvent);
        } catch (JsonProcessingException e) {
            log.error("Ошибка сериализации объекта для отправки в топик {}: {}", topic, merchantCallbackEvent);
            throw new BodyMappingException("Error occurred while mapping orderConfirmationEvent", e);
        }
    }

}
