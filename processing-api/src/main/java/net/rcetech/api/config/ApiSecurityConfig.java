package net.rcetech.api.config;

import net.rcetech.domain.service.clients.ApiKeyService;
import net.rcetech.meta.WebPath;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@Profile("!disable-security")
public class ApiSecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, ApiKeyService apiKeyService) {
        http.securityMatcher(WebPath.V1_API_PATH + "/**")
                .authorizeHttpRequests(auth ->
                        auth.requestMatchers(WebPath.V1_API_PATH + "/**").hasRole("CLIENT"))
                .addFilterBefore(
                        new ApiKeyAuthenticationFilter(apiKeyService),
                        UsernamePasswordAuthenticationFilter.class
                );
        return http.build();
    }
}
