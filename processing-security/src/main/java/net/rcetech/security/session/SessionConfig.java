package net.rcetech.security.session;

import org.springframework.boot.session.autoconfigure.DefaultCookieSerializerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SessionConfig {

    @Bean
    public DefaultCookieSerializerCustomizer plainSessionCookieCustomizer() {
        return serializer -> serializer.setUseBase64Encoding(false);
    }
}
