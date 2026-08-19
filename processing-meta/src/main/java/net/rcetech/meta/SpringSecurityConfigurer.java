package net.rcetech.meta;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * Конфигуратор spring-security. Для конфигурации необходимо
 */
@FunctionalInterface
public interface SpringSecurityConfigurer {

    void configure(HttpSecurity http);
}
