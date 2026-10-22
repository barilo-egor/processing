package net.rcetech.domain.test;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.mysql.MySQLContainer;

public final class SharedMySQLContainer {

    private static final String URL_PROPERTY = "processing.test.mysql.url";

    private static final String USERNAME_PROPERTY = "processing.test.mysql.username";

    private static final String PASSWORD_PROPERTY = "processing.test.mysql.password";

    private SharedMySQLContainer() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        if (System.getProperty(URL_PROPERTY) != null) {
            registry.add("spring.datasource.url", () -> System.getProperty(URL_PROPERTY));
            registry.add("spring.datasource.username", () -> System.getProperty(USERNAME_PROPERTY));
            registry.add("spring.datasource.password", () -> System.getProperty(PASSWORD_PROPERTY));
        } else {
            registry.add("spring.datasource.url", LocalContainer.INSTANCE::getJdbcUrl);
            registry.add("spring.datasource.username", LocalContainer.INSTANCE::getUsername);
            registry.add("spring.datasource.password", LocalContainer.INSTANCE::getPassword);
        }
        registry.add("spring.flyway.locations", () -> "classpath:database/migration");
    }

    private static final class LocalContainer {

        private static final MySQLContainer INSTANCE = new MySQLContainer("mysql:8.0.46");

        static {
            INSTANCE.start();
        }
    }
}
