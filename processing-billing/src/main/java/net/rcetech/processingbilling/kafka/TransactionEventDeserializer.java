package net.rcetech.processingbilling.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Deserializer;
import net.rcetech.processingbilling.dto.TransactionDTO;
import net.rcetech.processingbilling.exceptions.BaseException;

import java.nio.charset.StandardCharsets;

@Slf4j
public class TransactionEventDeserializer implements Deserializer<TransactionDTO> {

    private final ObjectMapper objectMapper;

    public TransactionEventDeserializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public TransactionDTO deserialize(String topic, byte[] data) {
        try {
            if (data == null)
                return null;
            return objectMapper.readValue(data, TransactionDTO.class);
        } catch (Exception e) {
            throw new BaseException(
                    "Error occurred while deserializer value: " + new String(data, StandardCharsets.UTF_8));
        }
    }

}
