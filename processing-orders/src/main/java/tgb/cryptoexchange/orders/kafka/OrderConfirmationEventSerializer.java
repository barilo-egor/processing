package tgb.cryptoexchange.orders.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serializer;
import tgb.cryptoexchange.orders.exceptions.BodyMappingException;

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
        } catch (JsonProcessingException e) {
            log.error("Ошибка сериализации объекта для отправки в топик {}: {}", topic, orderConfirmationEvent);
            throw new BodyMappingException("Error occurred while mapping orderConfirmationEvent", e);
        }
    }

}
