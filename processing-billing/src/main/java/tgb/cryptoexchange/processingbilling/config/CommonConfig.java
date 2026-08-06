package tgb.cryptoexchange.processingbilling.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;
import tgb.cryptoexchange.processingbilling.dto.TransactionDTO;
import tgb.cryptoexchange.processingbilling.kafka.ErrorHandler;
import tgb.cryptoexchange.processingbilling.kafka.TransactionEventDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableAsync
@EnableScheduling
@EnableKafka
public class CommonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }

    @Bean
    @Profile("!kafka-disabled")
    public ConsumerFactory<String, TransactionDTO> transactionEventConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers, ObjectMapper objectMapper) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        StringDeserializer keyDeserializer = new StringDeserializer();

        TransactionEventDeserializer targetValueDeserializer = new TransactionEventDeserializer(objectMapper);

        return new DefaultKafkaConsumerFactory<>(props, keyDeserializer, targetValueDeserializer);
    }

    @Bean
    @Profile("!kafka-disabled")
    public ConcurrentKafkaListenerContainerFactory<String, TransactionDTO> transactionKafkaListenerContainerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            ObjectMapper objectMapper, ErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, TransactionDTO> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setCommonErrorHandler(defaultErrorHandler(errorHandler));
        factory.setConsumerFactory(transactionEventConsumerFactory(bootstrapServers, objectMapper));
        return factory;
    }

    @Bean
    @Profile("!kafka-disabled")
    public DefaultErrorHandler defaultErrorHandler(ErrorHandler errorHandler) {
        return new DefaultErrorHandler(
                errorHandler::handle,
                new FixedBackOff(60000, 1)
        );
    }

}
