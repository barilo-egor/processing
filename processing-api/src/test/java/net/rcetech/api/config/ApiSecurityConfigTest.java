package net.rcetech.api.config;

import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.service.clients.ApiKeyService;
import net.rcetech.meta.WebPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ApiSecurityConfigTest {

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Container
    static MySQLContainer mySQLContainer = new MySQLContainer("mysql:8.0.46");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mySQLContainer::getUsername);
        registry.add("spring.datasource.password", mySQLContainer::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:database/migration");
    }

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private MockMvc mockMvc;

    Client getDummyClient() {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setUsername("test" + client.getId());
        client.setRegisteredAt(Instant.now());
        return clientRepository.save(client);
    }

    @Test
    @DisplayName("Запросы с авторизацией по апи ключу должны иметь доступ к пути по /order, в том числе без csrf токена.")
    void authWithApiKeyShouldHaveAccessToOrderPathWithoutCsrf() throws Exception {
        Client client = getDummyClient();
        String apiKey = apiKeyService.create("key", client);

        mockMvc.perform(get(WebPath.V1_API_PATH + "/order")
                .header("Api-Key", apiKey)
        ).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Запросы с любой авторизацией должны иметь доступ к пути по /order.")
    void authWithApiKeyShouldNotHaveAccessToWithdrawalRequestsPath() throws Exception {
        mockMvc.perform(get(WebPath.V1_API_PATH + "/order")
                .with(user(UUID.randomUUID().toString()).roles("CLIENT"))
        ).andExpect(status().isOk());
    }
}