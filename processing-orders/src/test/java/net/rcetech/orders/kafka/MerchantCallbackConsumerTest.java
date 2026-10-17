package net.rcetech.orders.kafka;

import net.rcetech.domain.model.incidents.Incident;
import net.rcetech.domain.repository.incidents.IncidentRepository;
import net.rcetech.domain.service.incidents.IncidentService;
import net.rcetech.meta.Profiles;
import net.rcetech.meta.incidents.IncidentStatus;
import net.rcetech.meta.incidents.IncidentType;
import net.rcetech.meta.orders.MerchantCallbackEvent;
import net.rcetech.orders.callback.OrderCallbackResolver;
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

import java.time.Duration;
import java.util.List;
import java.util.Properties;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@SpringBootTest
@ActiveProfiles(Profiles.DEV)
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
        public MerchantCallbackConsumer merchantCallbackConsumer(OrderCallbackResolver orderCallbackResolver,
                                                                 IncidentService incidentService) {
            return new MerchantCallbackConsumer(orderCallbackResolver, incidentService);
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
        registry.add("spring.flyway.locations", () -> "classpath:database/migration");
    }

    @Autowired
    private KafkaProducer<String, String> callbackProducer;

    @MockitoBean
    private OrderCallbackResolver orderCallbackResolver;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @BeforeEach
    void setUp() {
        Mockito.reset(orderCallbackResolver);
        incidentRepository.deleteAll();
    }

    @ParameterizedTest
    @CsvSource("""
            1236043,DISPUTE,Спор,ALFA_TEAM
            """)
    @DisplayName("Метод должен передать КБ в метод сервиса.")
    void callback_shouldPassCallbackToServiceMethod(String merchantOrderId, String status, String statusDescription,
                                                    Merchant merchant) {
        String callbackJsonTemplate = "{" +
                "\"merchantOrderId\":\"%s\", " +
                "\"status\":\"%s\", " +
                "\"statusDescription\": \"%s\"," +
                "\"merchant\":\"%s\"" +
                "}";
        String message = String.format(callbackJsonTemplate, merchantOrderId, status, statusDescription, merchant);
        String callbackTopic = "merchant-details-callback-v1";
        callbackProducer.send(new ProducerRecord<>(callbackTopic, merchantOrderId, message));
        ArgumentCaptor<MerchantCallbackEvent> captor = ArgumentCaptor.forClass(MerchantCallbackEvent.class);

        verify(orderCallbackResolver, timeout(10000)).resolve(captor.capture());

        MerchantCallbackEvent actual = captor.getValue();
        assertAll(
                () -> assertNotNull(actual, "Event не должен быть null"),
                () -> assertEquals(merchantOrderId, actual.getMerchantOrderId()),
                () -> assertEquals(status, actual.getStatus()),
                () -> assertEquals(statusDescription, actual.getStatusDescription()),
                () -> assertEquals(merchant.name(), String.valueOf(actual.getMerchant()))
        );
    }

    @ParameterizedTest
    @CsvSource("""
            1236043,DISPUTE,Спор,ALFA_TEAM
            c2cbb845-7313-4106-a1a3-e9e4b328bbd9,CANCELED,Отменен,EVO_PAY
            """)
    @DisplayName("Метод должен создать инцидент, если при обработке КБ произошла ошибка.")
    void callback_shouldCreateIncidentIfCantResolveCallback(String merchantOrderId, String status,
                                                            String statusDescription, Merchant merchant) {
        doThrow(new RuntimeException("Ошибка обработки КБ")).when(orderCallbackResolver).resolve(any());
        String callbackJsonTemplate = "{" +
                "\"merchantOrderId\":\"%s\", " +
                "\"status\":\"%s\", " +
                "\"statusDescription\": \"%s\"," +
                "\"merchant\":\"%s\"" +
                "}";
        String message = String.format(callbackJsonTemplate, merchantOrderId, status, statusDescription, merchant);
        String callbackTopic = "merchant-details-callback-v1";
        long before = System.currentTimeMillis();
        callbackProducer.send(new ProducerRecord<>(callbackTopic, merchantOrderId, message));

        await().atMost(Duration.ofSeconds(10)).until(() -> incidentRepository.count() > 0);
        long after = System.currentTimeMillis();

        List<Incident> incidents = incidentRepository.findAll();
        assertEquals(1, incidents.size());
        Incident incident = incidents.getFirst();
        assertAll(
                () -> assertNotNull(incident.getId()),
                () -> assertNotNull(incident.getCreatedAt()),
                () -> assertNotNull(incident.getErrorTimestamp()),
                () -> assertTrue(incident.getErrorTimestamp() >= before && incident.getErrorTimestamp() <= after),
                () -> assertEquals(IncidentStatus.NEW, incident.getStatus()),
                () -> assertEquals(IncidentType.MERCHANT_CALLBACK_HANDLE_ERROR, incident.getType()),
                () -> assertEquals(merchant.name(), incident.getData().get("merchant")),
                () -> assertEquals(status, incident.getData().get("status")),
                () -> assertEquals(statusDescription, incident.getData().get("statusDescription")),
                () -> assertEquals(merchantOrderId, incident.getData().get("merchantOrderId"))
        );
    }
}
