package net.rcetech.orders.kafka;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.orders.exceptions.BodyMappingException;
import org.apache.kafka.common.serialization.Serializer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
public class UnknownStatusSerializer implements Serializer<net.rcetech.meta.orders.MerchantCallbackEvent> {

    private final ObjectMapper objectMapper;

    public UnknownStatusSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public byte[] serialize(String topic, net.rcetech.meta.orders.MerchantCallbackEvent merchantCallbackEvent) {
        try {
            if (merchantCallbackEvent == null) {
                return new byte[0];
            }
            return objectMapper.writeValueAsBytes(merchantCallbackEvent);
        } catch (JacksonException e) {
            log.error("Ошибка сериализации объекта для отправки в топик {}: {}", topic, merchantCallbackEvent);
            throw new BodyMappingException("Error occurred while mapping orderConfirmationEvent", e);
        }
    }

}
