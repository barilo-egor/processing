package net.rcetech.clients.service.integration;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.LocalGrpcPort;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import net.rcetech.clients.repository.ClientRefreshTokenRepository;
import net.rcetech.clients.repository.ClientRepository;
import net.rcetech.clients.repository.WithdrawalRequestRepository;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.grpc.server.port=0")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
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

    @LocalGrpcPort
    protected int port;

    protected ManagedChannel channel;

    @Autowired
    protected ClientRepository clientRepository;

    @Autowired
    protected WithdrawalRequestRepository withdrawalRequestRepository;

    @Autowired
    protected ClientRefreshTokenRepository tokenRepository;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);

        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @BeforeEach
    void initChannel() {
        channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .build();
    }

    @BeforeEach
    @Transactional
    void clearDatabase() {
        tokenRepository.deleteAllInBatch();
        withdrawalRequestRepository.deleteAllInBatch();
        clientRepository.deleteAllInBatch();
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdownNow();
        }
    }

}
