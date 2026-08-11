package net.rcetech.api;

import net.rcetech.api.controller.handler.ApiExceptionHandler;
import net.rcetech.clients.service.ClientApi;
import net.rcetech.grpc.generated.MerchantDetailsServiceGrpc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import net.rcetech.orders.service.OrderApi;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.grpc.server.port=0")
@Import(ApiExceptionHandler.class)
public abstract class ApiBaseIntegrationTest {

    protected static final String API_KEY = "professional-test-token";

    protected static final String CLIENT_SECRET = "super-secret-key-for-hmac-verification-12345";

    protected WebTestClient webTestClient;

    @LocalServerPort
    protected int port;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    protected RedisTemplate<String, Object> redisTemplate;

    @MockitoBean
    protected MerchantDetailsServiceGrpc.MerchantDetailsServiceFutureStub merchantDetailsServiceFutureStub;

    @MockitoBean
    protected OrderApi orderApi;

    @MockitoBean
    protected ClientApi clientApi;

    @BeforeEach
    void setUpWebTestClient() {
        this.webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/api/v1")
                .responseTimeout(Duration.ofSeconds(90))
                .build();
    }

    protected String calculateHmacSha256(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(CLIENT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

}
