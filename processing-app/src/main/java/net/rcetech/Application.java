package net.rcetech;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;

@SpringBootApplication(exclude = DataRedisRepositoriesAutoConfiguration.class)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
