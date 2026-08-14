package net.rcetech;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

public interface CommonContainers {

    @Container
    @ServiceConnection
    MySQLContainer mysqlContainer = new MySQLContainer(DockerImageName.parse("mysql:8.0.46"));

    @Container
    @ServiceConnection
    KafkaContainer kafkaContainer = new KafkaContainer(DockerImageName.parse("apache/kafka"));
}
