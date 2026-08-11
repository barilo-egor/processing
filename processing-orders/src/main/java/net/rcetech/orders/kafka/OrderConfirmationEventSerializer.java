package net.rcetech.orders.kafka;

import lombok.extern.slf4j.Slf4j;
import net.rcetech.orders.exceptions.BodyMappingException;
import org.apache.kafka.common.serialization.Serializer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
public class OrderConfirmationEventSerializer implements Serializer<OrderConfirmationEvent> {

    private final ObjectMapper objectMapper;

    public OrderConfirmationEventSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public byte[] serialize(String topic, OrderConfirmationEvent orderConfirmationEvent) {
        try {
            if (orderConfirmationEvent == null) {
                return new byte[0];
            }
            return objectMapper.writeValueAsBytes(orderConfirmationEvent);
        } catch (JacksonException e) {
            log.error("Ошибка сериализации объекта для отправки в топик {}: {}", topic, orderConfirmationEvent);
            throw new BodyMappingException("Error occurred while mapping orderConfirmationEvent", e);
        }
    }

}
