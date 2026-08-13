package net.rcetech.orders.config;

import net.rcetech.meta.KafkaLogErrorHandler;
import net.rcetech.meta.orders.MerchantCallbackEvent;
import net.rcetech.orders.kafka.MerchantCallbackEventDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableAsync
@EnableScheduling
@EnableKafka
public class OrdersCommonConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(30));
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }

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
