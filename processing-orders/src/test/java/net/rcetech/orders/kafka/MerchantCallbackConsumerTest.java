package net.rcetech.orders.kafka;

import net.rcetech.domain.model.orders.MerchantCallback;
import net.rcetech.domain.repository.orders.MerchantCallbackRepository;
import net.rcetech.domain.service.orders.MerchantCallbackService;
import net.rcetech.meta.Profiles;
import net.rcetech.meta.incidents.MerchantCallbackHandleErrorEvent;
import net.rcetech.meta.orders.MerchantCallbackEvent;
import net.rcetech.orders.callback.OrderCallbackResolver;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
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
import java.util.concurrent.CopyOnWriteArrayList;

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

    static class IncidentEventCollector {

        private final List<MerchantCallbackHandleErrorEvent> events = new CopyOnWriteArrayList<>();

        @EventListener
        public void collect(MerchantCallbackHandleErrorEvent event) {
            events.add(event);
        }
    }

    @TestConfiguration
    static class Configuration {

        @Bean
        public IncidentEventCollector incidentEventCollector() {
            return new IncidentEventCollector();
        }

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
                                                                 MerchantCallbackService merchantCallbackService,
                                                                 ApplicationEventPublisher eventPublisher) {
            return new MerchantCallbackConsumer(orderCallbackResolver, merchantCallbackService, eventPublisher);
        }
    }

    private static final String CALLBACK_TOPIC = "merchant-details-callback-v1";

    private static final String CALLBACK_JSON_TEMPLATE = "{" +
            "\"merchantOrderId\":\"%s\", " +
            "\"status\":\"%s\", " +
            "\"statusDescription\": \"%s\"," +
            "\"merchant\":\"%s\"" +
            "}";

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
    private IncidentEventCollector incidentEventCollector;

    @Autowired
    private MerchantCallbackRepository merchantCallbackRepository;

    @BeforeEach
    void setUp() {
        Mockito.reset(orderCallbackResolver);
        incidentEventCollector.events.clear();
        merchantCallbackRepository.deleteAll();
    }

    @ParameterizedTest
    @CsvSource("""
            1236043,DISPUTE,Спор,ALFA_TEAM
            c2cbb845-7313-4106-a1a3-e9e4b328bbd9,CANCELED,Отменен,EVO_PAY
            """)
    @DisplayName("Метод должен сохранить КБ до его обработки, даже если обработка завершилась ошибкой.")
    void callback_shouldSaveCallbackBeforeResolving(String merchantOrderId, String status, String statusDescription,
                                                    Merchant merchant) {
        doThrow(new RuntimeException("Ошибка обработки КБ")).when(orderCallbackResolver).resolve(any());
        String message = String.format(CALLBACK_JSON_TEMPLATE, merchantOrderId, status, statusDescription, merchant);
        callbackProducer.send(new ProducerRecord<>(CALLBACK_TOPIC, merchantOrderId, message));

        await().atMost(Duration.ofSeconds(10)).until(() -> !incidentEventCollector.events.isEmpty());

        List<MerchantCallback> callbacks = merchantCallbackRepository.findAll();
        assertEquals(1, callbacks.size());
        MerchantCallback actual = callbacks.getFirst();
        assertAll(
                () -> assertNull(actual.getOrder()),
                () -> assertNull(actual.getProcessedAt()),
                () -> assertEquals(merchant, actual.getMerchant()),
                () -> assertEquals(merchantOrderId, actual.getMerchantOrderId()),
                () -> assertEquals(status, actual.getStatus()),
                () -> assertEquals(statusDescription, actual.getStatusDescription())
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"1236043", "c2cbb845-7313-4106-a1a3-e9e4b328bbd9"})
    @DisplayName("Метод должен создать инцидент и не обрабатывать КБ, если значение сообщения пустое.")
    void callback_shouldCreateIncidentIfValueNull(String key) {
        callbackProducer.send(new ProducerRecord<>(CALLBACK_TOPIC, key, null));

        await().atMost(Duration.ofSeconds(10)).until(() -> !incidentEventCollector.events.isEmpty());

        assertEquals(1, incidentEventCollector.events.size());
        assertAll(
                () -> assertNull(incidentEventCollector.events.getFirst().merchantOrderId()),
                () -> assertEquals(0, merchantCallbackRepository.count())
        );
        verify(orderCallbackResolver, never()).resolve(any());
    }

    @ParameterizedTest
    @CsvSource("""
            1236043,DISPUTE,Спор,UNKNOWN_MERCHANT
            c2cbb845-7313-4106-a1a3-e9e4b328bbd9,CANCELED,Отменен,NOT_A_MERCHANT
            """)
    @DisplayName("Метод должен создать инцидент и не обрабатывать КБ, если КБ не удалось сохранить.")
    void callback_shouldCreateIncidentAndSkipResolvingIfCallbackNotSaved(String merchantOrderId, String status,
                                                                          String statusDescription, String merchant) {
        String message = String.format(CALLBACK_JSON_TEMPLATE, merchantOrderId, status, statusDescription, merchant);
        callbackProducer.send(new ProducerRecord<>(CALLBACK_TOPIC, merchantOrderId, message));

        await().atMost(Duration.ofSeconds(10)).until(() -> !incidentEventCollector.events.isEmpty());

        assertEquals(1, incidentEventCollector.events.size());
        assertAll(
                () -> assertEquals(merchantOrderId, incidentEventCollector.events.getFirst().merchantOrderId()),
                () -> assertEquals(0, merchantCallbackRepository.count())
        );
        verify(orderCallbackResolver, never()).resolve(any());
    }

    @ParameterizedTest
    @CsvSource("""
            1236043,DISPUTE,Спор,ALFA_TEAM
            c2cbb845-7313-4106-a1a3-e9e4b328bbd9,CANCELED,Отменен,EVO_PAY
            """)
    @DisplayName("Метод должен передать КБ в метод сервиса.")
    void callback_shouldPassCallbackToServiceMethod(String merchantOrderId, String status, String statusDescription,
                                                    Merchant merchant) {
        String message = String.format(CALLBACK_JSON_TEMPLATE, merchantOrderId, status, statusDescription, merchant);
        callbackProducer.send(new ProducerRecord<>(CALLBACK_TOPIC, merchantOrderId, message));
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
        String message = String.format(CALLBACK_JSON_TEMPLATE, merchantOrderId, status, statusDescription, merchant);
        long before = System.currentTimeMillis();
        callbackProducer.send(new ProducerRecord<>(CALLBACK_TOPIC, merchantOrderId, message));

        await().atMost(Duration.ofSeconds(10)).until(() -> !incidentEventCollector.events.isEmpty());
        long after = System.currentTimeMillis();

        assertEquals(1, incidentEventCollector.events.size());
        MerchantCallbackHandleErrorEvent event = incidentEventCollector.events.getFirst();
        assertAll(
                () -> assertTrue(event.errorTimestamp() >= before && event.errorTimestamp() <= after),
                () -> assertEquals(merchant, event.merchant()),
                () -> assertEquals(status, event.status()),
                () -> assertEquals(statusDescription, event.statusDescription()),
                () -> assertEquals(merchantOrderId, event.merchantOrderId())
        );
    }
}
