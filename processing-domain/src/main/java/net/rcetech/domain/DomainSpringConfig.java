package net.rcetech.domain;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class DomainSpringConfig {

    private final Environment env;

    public DomainSpringConfig(Environment env) {
        this.env = env;
    }

    @PostConstruct
    public void init() {
        String hbm2ddl = env.getProperty("spring.jpa.hibernate.ddl-auto");
        String profile = env.getProperty("spring.profiles.active");
        if (!"dev".equals(profile) && !"validate".equals(hbm2ddl)) {
            throw new IllegalStateException(
                    "Свойство spring.jpa.hibernate.ddl-auto вне профиля dev может быть установлено только в режим validate."
            );
        }
    }
}
