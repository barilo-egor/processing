package tgb.cryptoexchange.clientsapi.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import tgb.cryptoexchange.clientsapi.dto.WithdrawalRequestDTO;
import tgb.cryptoexchange.clientsapi.kafka.WithdrawalReceiveProducerListener;
import tgb.cryptoexchange.clientsapi.kafka.WithdrawalRequestReceiveEventSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableAsync
public class CommonConfig {

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    @Bean
    @Profile("!kafka-disabled")
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
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
    public KafkaTemplate<String, WithdrawalRequestDTO> kafkaTemplate(
            WithdrawalReceiveProducerListener withdrawalReceiveProducerListener,
            KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
        KafkaTemplate<String, WithdrawalRequestDTO> kafkaTemplate = new KafkaTemplate<>(
                withdrawalRequestProducerFactory(kafkaProperties, objectMapper));
        kafkaTemplate.setProducerListener(withdrawalReceiveProducerListener);
        return kafkaTemplate;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
