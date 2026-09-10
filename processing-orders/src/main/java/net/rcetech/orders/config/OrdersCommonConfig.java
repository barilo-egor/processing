package net.rcetech.orders.config;

import net.rcetech.meta.KafkaLogErrorHandler;
import net.rcetech.meta.orders.MerchantCallbackEvent;
import net.rcetech.orders.kafka.MerchantCallbackEventDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
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

import java.util.Map;

@Configuration
@EnableAsync
@EnableScheduling
@EnableKafka
public class OrdersCommonConfig {

    @Bean
    @Profile("!kafka-disabled")
    public ConsumerFactory<String, MerchantCallbackEvent> merchantCallbackEventConsumerFactory(
            KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        StringDeserializer keyDeserializer = new StringDeserializer();
        MerchantCallbackEventDeserializer targetValueDeserializer = new MerchantCallbackEventDeserializer(objectMapper);
        return new DefaultKafkaConsumerFactory<>(props, keyDeserializer, targetValueDeserializer);
    }

    @Bean
    @Profile("!kafka-disabled")
    public ConcurrentKafkaListenerContainerFactory<String, MerchantCallbackEvent> merchantCallbackKafkaListenerContainerFactory(
            ConsumerFactory<String, MerchantCallbackEvent> merchantCallbackEventConsumerFactory,
            KafkaLogErrorHandler kafkaLogErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, MerchantCallbackEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setCommonErrorHandler(kafkaLogErrorHandler);
        factory.setConsumerFactory(merchantCallbackEventConsumerFactory);
        return factory;
    }
}
