package net.rcetech.meta.config;

import net.rcetech.meta.Profiles;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Arrays;

@Configuration
@Profile("!disable-security")
public class ActuatorSecurityConfig {

    public ActuatorSecurityConfig(Environment environment) {
        if (Arrays.stream(environment.getActiveProfiles()).noneMatch(Profiles.DEV::equals)
                && ManagementPortType.get(environment) == ManagementPortType.SAME) {
            throw new IllegalStateException(
                    "Порт actuator management.server.port вне профиля dev должен отличаться от порта приложения server.port.");
        }
    }

    @Bean
    @Order(1)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) {
        http.securityMatcher(EndpointRequest.to("health", "prometheus"))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
