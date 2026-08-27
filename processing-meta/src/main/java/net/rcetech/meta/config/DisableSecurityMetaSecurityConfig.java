package net.rcetech.meta.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("disable-security")
public class DisableSecurityMetaSecurityConfig {

    @Bean
    public SecurityFilterChain globalDevFilterChain(HttpSecurity http) {
        http.authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
        );
        http.csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
