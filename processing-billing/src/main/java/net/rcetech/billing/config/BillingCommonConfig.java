package net.rcetech.billing.config;

import net.rcetech.meta.billing.dto.TransactionDTO;
import net.rcetech.billing.kafka.TransactionEventDeserializer;
import net.rcetech.meta.KafkaLogErrorHandler;
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
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableAsync
@EnableScheduling
@EnableKafka
public class BillingCommonConfig {

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
            ObjectMapper objectMapper, KafkaLogErrorHandler kafkaLogErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, TransactionDTO> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setCommonErrorHandler(kafkaLogErrorHandler);
        factory.setConsumerFactory(transactionEventConsumerFactory(bootstrapServers, objectMapper));
        return factory;
    }

}
