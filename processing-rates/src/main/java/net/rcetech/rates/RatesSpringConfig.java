package net.rcetech.rates;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RatesSpringConfig {

    @Bean
    public RestClient rapiraRestClient() {
        return RestClient.builder()
                .baseUrl("https://api.rapira.net")
                .build();
    }
}
