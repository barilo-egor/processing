package net.rcetech.meta.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActuatorSecurityConfigPortCheckTest {

    @ParameterizedTest
    @CsvSource({
            ",,",
            "9000,,",
            "8080,8080,mh-test",
            "8443,8443,md-test-details;md-test-callback"
    })
    @DisplayName("Конфигурация вне профиля dev должна бросить исключение, если actuator слушает порт приложения.")
    void constructor_shouldThrowIfManagementPortSameAsServerPort(String serverPort,
                                                                 String managementPort,
                                                                 String profiles) {
        MockEnvironment environment = environment(serverPort, managementPort, profiles);

        assertThrows(IllegalStateException.class, () -> new ActuatorSecurityConfig(environment));
    }

    @ParameterizedTest
    @CsvSource({
            ",8091,",
            "8080,8091,mh-test",
            "8443,9100,",
            "8080,-1,",
            ",0,",
            ",,dev",
            "8080,8080,dev;mh-test"
    })
    @DisplayName("Конфигурация должна создаться, если actuator слушает отдельный порт, выключен или активен профиль dev.")
    void constructor_shouldNotThrowIfManagementPortDifferentOrDisabledOrDev(String serverPort,
                                                                           String managementPort,
                                                                           String profiles) {
        MockEnvironment environment = environment(serverPort, managementPort, profiles);

        assertDoesNotThrow(() -> new ActuatorSecurityConfig(environment));
    }

    private MockEnvironment environment(String serverPort, String managementPort, String profiles) {
        MockEnvironment environment = new MockEnvironment();
        if (profiles != null) {
            environment.setActiveProfiles(profiles.split(";"));
        }
        if (serverPort != null) {
            environment.setProperty("server.port", serverPort);
        }
        if (managementPort != null) {
            environment.setProperty("management.server.port", managementPort);
        }
        return environment;
    }
}
