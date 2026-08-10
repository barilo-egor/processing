package net.rcetech.details.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import net.rcetech.details.service.ClientAuthService;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ClientAuthService clientAuthService;

    private final ObjectMapper objectMapper;

    public SecurityConfig(ClientAuthService clientAuthService, ObjectMapper objectMapper) {
        this.clientAuthService = clientAuthService;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/**").permitAll()
                )
                .addFilterBefore(new ApiSignatureFilter(clientAuthService, objectMapper),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

}
