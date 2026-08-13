package net.rcetech;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;

@TestConfiguration
public class IntegrationTestsConfiguration {

    @Bean
    @ServiceConnection
    public MySQLContainer mySQLContainer() {
        return new MySQLContainer("mysql:8.0");
    }

    @Bean
    @ServiceConnection
    public KafkaContainer kafkaContainer() {
        return new KafkaContainer("apache/kafka");
    }
}
