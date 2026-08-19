package net.rcetech.clients.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import net.rcetech.meta.SpringSecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class ClientsCommonConfig {

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

//    @Bean
//    public SpringSecurityConfigurer clientsSecurityConfigurer() {
//        return http -> http.
//    }
}
