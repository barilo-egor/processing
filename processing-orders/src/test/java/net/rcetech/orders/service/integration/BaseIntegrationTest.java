package net.rcetech.orders.service.integration;

import tools.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import net.rcetech.orders.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.InjectWireMock;

@ActiveProfiles("test")
@SpringBootTest
@RecordApplicationEvents
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(WireMockConfiguration.class)
@ConfigureWireMock(
        name = "client-service",
        baseUrlProperties = { "app.webclient.base-url" }
)
@Testcontainers
public abstract class BaseIntegrationTest {

    @SuppressWarnings("resource")
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withReuse(true);

    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"));

    static {
        mysql.start();
        kafka.start();
    }

    @InjectWireMock("client-service")
    protected WireMockServer wireMockServer;

    protected WireMock wireMockClient;

    @Autowired
    protected OrderRepository orderRepository;

    @Autowired
    protected KafkaProperties kafkaProperties;

    @Autowired
    protected ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);

        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @BeforeEach
    void initWireMockClient() {
        wireMockClient = new WireMock("localhost", wireMockServer.port());
        wireMockClient.resetMappings();
    }

    @BeforeEach
    void clearDatabase() {
        orderRepository.deleteAllInBatch();
    }

}
