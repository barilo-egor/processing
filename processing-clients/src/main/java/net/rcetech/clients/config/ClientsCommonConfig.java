package net.rcetech.clients.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import net.rcetech.meta.clients.dto.WithdrawalRequestDTO;
import net.rcetech.clients.kafka.WithdrawalReceiveProducerListener;
import net.rcetech.clients.kafka.WithdrawalRequestReceiveEventSerializer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableAsync
public class ClientsCommonConfig {

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    @Bean
    @Profile("!kafka-disabled")
    public ProducerFactory<String, WithdrawalRequestDTO> withdrawalRequestProducerFactory(
            KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        return new DefaultKafkaProducerFactory<>(
                configProps,
                new StringSerializer(),
                new WithdrawalRequestReceiveEventSerializer(objectMapper)
        );
    }

    @Bean
    @Profile("!kafka-disabled")
    public KafkaTemplate<String, WithdrawalRequestDTO> withdrawalRequestKafkaTemplate(
            WithdrawalReceiveProducerListener withdrawalReceiveProducerListener,
            KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
        KafkaTemplate<String, WithdrawalRequestDTO> kafkaTemplate = new KafkaTemplate<>(
                withdrawalRequestProducerFactory(kafkaProperties, objectMapper));
        kafkaTemplate.setProducerListener(withdrawalReceiveProducerListener);
        return kafkaTemplate;
    }

}
