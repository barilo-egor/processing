package net.rcetech.clients;

import net.rcetech.IntegrationTestsConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(IntegrationTestsConfiguration.class)
class ClientRegistrationIT {

    @Test
    void test() {
        assertTrue(true);
    }
}
