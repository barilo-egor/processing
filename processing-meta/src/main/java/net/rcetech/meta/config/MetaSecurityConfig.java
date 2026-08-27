package net.rcetech.meta.config;

import net.rcetech.meta.SpringSecurityConfigurer;
import net.rcetech.meta.user.KeycloakRoleConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@Configuration
@EnableMethodSecurity
@Profile("!disable-security")
public class MetaSecurityConfig {

    @Bean
    public SecurityFilterChain globalFilterChain(HttpSecurity http, List<SpringSecurityConfigurer> configurers) {
        http.authorizeHttpRequests(auth -> auth
                .anyRequest().authenticated()
        );
        for (SpringSecurityConfigurer configurer : configurers) {
            configurer.configure(http);
        }
        http.oauth2Login(oAuth2 ->
                oAuth2.defaultSuccessUrl("/dashboard")
        );
        http.csrf(CsrfConfigurer::spa);
        return http.build();
    }

    @Bean
    public KeycloakRoleConverter keycloakRoleConverter() {
        return new KeycloakRoleConverter();
    }

    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN").implies("OPERATOR", "USER")
                .role("OPERATOR").implies("USER")
                .build();
    }
}
