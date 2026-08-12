package net.rcetech.orders.config;

import net.rcetech.meta.KafkaLogErrorHandler;
import net.rcetech.orders.kafka.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;
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
    public ProducerFactory<String, OrderConfirmationEvent> orderConfirmationProducerFactory(
            KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        return new DefaultKafkaProducerFactory<>(
                configProps,
                new StringSerializer(),
                new OrderConfirmationEventSerializer(objectMapper)
        );
    }

    @Bean
    @Profile("!kafka-disabled")
    public KafkaTemplate<String, OrderConfirmationEvent> orderConfirmationEventKafkaTemplate(
            OrderConfirmationProducerListener orderConfirmationProducerListener,
            KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
        KafkaTemplate<String, OrderConfirmationEvent> kafkaTemplate = new KafkaTemplate<>(
                orderConfirmationProducerFactory(kafkaProperties, objectMapper));
        kafkaTemplate.setProducerListener(orderConfirmationProducerListener);
        return kafkaTemplate;
    }

    @Bean
    @Profile("!kafka-disabled")
    public ConsumerFactory<String, MerchantCallbackEvent> merchantCallbackEventConsumerFactory(
            @Value("${spring.kafka.merchant-details.bootstrap-servers}") String bootstrapServers,
            ObjectMapper objectMapper) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        StringDeserializer keyDeserializer = new StringDeserializer();

        MerchantCallbackEventDeserializer targetValueDeserializer = new MerchantCallbackEventDeserializer(objectMapper);

        return new DefaultKafkaConsumerFactory<>(props, keyDeserializer, targetValueDeserializer);
    }

    @Bean
    @Profile("!kafka-disabled")
    public ConcurrentKafkaListenerContainerFactory<String, MerchantCallbackEvent> merchantCallbackKafkaListenerContainerFactory(
            @Value("${spring.kafka.merchant-details.bootstrap-servers}") String bootstrapServers,
            ObjectMapper objectMapper, KafkaLogErrorHandler kafkaLogErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, MerchantCallbackEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setCommonErrorHandler(kafkaLogErrorHandler);
        factory.setConsumerFactory(merchantCallbackEventConsumerFactory(bootstrapServers, objectMapper));
        return factory;
    }
}
