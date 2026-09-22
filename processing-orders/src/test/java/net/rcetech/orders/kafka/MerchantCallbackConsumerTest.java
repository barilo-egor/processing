package net.rcetech.orders.kafka;

import net.rcetech.meta.orders.MerchantCallbackEvent;
import net.rcetech.orders.callback.OrderCallbackService;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@SpringBootTest
@ActiveProfiles("dev")
@ImportAutoConfiguration(KafkaAutoConfiguration.class)
class MerchantCallbackConsumerTest {

    @TestConfiguration
    static class Configuration {

        @Bean
        public KafkaProducer<String, String> callbackProducer(KafkaProperties kafkaProperties) {
            Properties config = new Properties();
            config.put("client.id", "client1");
            config.put("bootstrap.servers", kafkaProperties.getBootstrapServers());
            config.put("acks", "all");
            return new KafkaProducer<>(config, new StringSerializer(), new StringSerializer());
        }

        @Bean
        public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> pf) {
            return new KafkaTemplate<>(pf);
        }

        @Bean
        public MerchantCallbackConsumer merchantCallbackConsumer(OrderCallbackService orderCallbackService) {
            return new MerchantCallbackConsumer(orderCallbackService);
        }
    }

    @Container
    static MySQLContainer mySQLContainer = new MySQLContainer("mysql:8.0.46");

    @Container
    static KafkaContainer kafkaContainer = new KafkaContainer("apache/kafka-native:3.8.0");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mySQLContainer::getUsername);
        registry.add("spring.datasource.password", mySQLContainer::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    }

    @Autowired
    private KafkaProducer<String, String> callbackProducer;

    private final String callbackTopic = "merchant-details-callback-v1";

    @MockitoBean
    private OrderCallbackService orderCallbackService;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @BeforeEach
    void setUp() {
        Mockito.reset(orderCallbackService);
    }

    private final String callbackJsonTemplate =
            "{" +
                    "\"merchantOrderId\":\"%s\", " +
                    "\"status\":\"%s\", " +
                    "\"statusDescription\": \"%s\"," +
                    "\"merchant\":\"%s\"" +
                    "}";

    @ParameterizedTest
    @CsvSource("""
            1236043,DISPUTE,Спор,ALFA_TEAM
            """)
    @DisplayName("Метод должен передать КБ в метод сервиса.")
    void callback_shouldPassCallbackToServiceMethod(String merchantOrderId, String status, String statusDescription,
                                                    Merchant merchant) {
        String message = String.format(callbackJsonTemplate, merchantOrderId, status, statusDescription, merchant);
        callbackProducer.send(new ProducerRecord<>(callbackTopic, merchantOrderId, message));
        ArgumentCaptor<MerchantCallbackEvent> captor = ArgumentCaptor.forClass(MerchantCallbackEvent.class);

        verify(orderCallbackService, timeout(10000)).resolve(captor.capture());

        MerchantCallbackEvent actual = captor.getValue();
        assertAll(
                () -> assertNotNull(actual, "Event не должен быть null"),
                () -> assertEquals(merchantOrderId, actual.getMerchantOrderId()),
                () -> assertEquals(status, actual.getStatus()),
                () -> assertEquals(statusDescription, actual.getStatusDescription()),
                () -> assertEquals(merchant.name(), String.valueOf(actual.getMerchant()))
        );
    }

}