package net.rcetech.clients.config;

import net.rcetech.meta.config.ProcessingConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class ClientsSecurityConfig {

    public static final String WEBHOOK_ROLE = "WEBHOOK_CLIENT";

    @Bean
    @Order(1)
    public SecurityFilterChain webhookSecurityFilterChain(HttpSecurity http) {
        http.securityMatcher("/api/private/client/event/")
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/private/client/event/"))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().hasRole(WEBHOOK_ROLE)
                )
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService(ProcessingConfigurationProperties config) {
        UserDetails webhookUser = User.withUsername(config.keycloak().webhook().username())
                .password(config.keycloak().webhook().password())
                .roles(WEBHOOK_ROLE)
                .build();
        return new InMemoryUserDetailsManager(webhookUser);
    }
}
