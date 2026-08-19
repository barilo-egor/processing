package net.rcetech.meta;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Configuration
public class MetaSpringConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .findAndAddModules()
                .build();
    }

    @Bean
    public SecurityFilterChain globalFilterChain(HttpSecurity http, List<SpringSecurityConfigurer> configurers) {
        http.oauth2Login(oAuth2 ->
                oAuth2.defaultSuccessUrl("/dashboard", true)
        );
        for (SpringSecurityConfigurer configurer : configurers) {
            configurer.configure(http);
        }
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/clients/open").permitAll()
                .requestMatchers("/clients/logout").permitAll()
                .anyRequest().authenticated()
        );
        return http.build();
    }
}
