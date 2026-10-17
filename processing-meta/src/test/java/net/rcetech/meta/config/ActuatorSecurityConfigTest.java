package net.rcetech.meta.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        classes = ActuatorSecurityConfigTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.server.port=0",
                "management.endpoints.web.exposure.include=health,prometheus,env,beans",
                "spring.kafka.bootstrap-servers=localhost:1"
        }
)
class ActuatorSecurityConfigTest {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int serverPort;

    @Value("${local.management.port}")
    private int managementPort;

    @ParameterizedTest
    @ValueSource(strings = { "/actuator/health", "/actuator/prometheus" })
    @DisplayName("Эндпоинт health или prometheus на порту управления должен быть доступен без авторизации.")
    void actuator_shouldPermitHealthAndPrometheusOnManagementPort(String path) throws Exception {
        assertEquals(200, get(managementPort, path));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/actuator/env", "/actuator/beans" })
    @DisplayName("Остальные эндпоинты actuator на порту управления должны требовать авторизации.")
    void actuator_shouldRequireAuthenticationForOtherEndpointsOnManagementPort(String path) throws Exception {
        assertEquals(401, get(managementPort, path));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/actuator/health", "/actuator/prometheus" })
    @DisplayName("Эндпоинт health или prometheus на основном порту должен требовать авторизации.")
    void actuator_shouldRequireAuthenticationOnServerPort(String path) throws Exception {
        assertEquals(401, get(serverPort, path));
    }

    private int get(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(ActuatorSecurityConfig.class)
    static class TestApplication {

        @Bean
        @Order(10)
        SecurityFilterChain testFallbackFilterChain(HttpSecurity http) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults());
            return http.build();
        }
    }
}
