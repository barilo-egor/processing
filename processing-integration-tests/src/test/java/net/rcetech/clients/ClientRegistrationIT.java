package net.rcetech.clients;

import net.rcetech.CommonContainers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ClientRegistrationIT implements CommonContainers {

    @Autowired
    private RestTestClient restTestClient;


    @Test
    void test() {
        restTestClient.post().uri("/clients")
                .body("{\"username\":\"user1\",\"password\":\"Password1!\"}")
                .header("Content-Type", "application/json")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk();
    }
}
