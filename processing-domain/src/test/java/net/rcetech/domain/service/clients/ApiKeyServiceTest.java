package net.rcetech.domain.service.clients;

import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.repository.clients.ApiKeyRepository;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.test.SharedMySQLContainer;
import net.rcetech.meta.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ApiKeyService.class)
class ApiKeyServiceTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        SharedMySQLContainer.register(registry);
    }

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ApiKeyService apiKeyService;

    @ParameterizedTest
    @ValueSource(ints = {0, 4, 9})
    @DisplayName("Ключ должен создаваться, пока у клиента меньше 10 ключей.")
    void create_shouldCreateKeyBelowLimit(int existingKeys) {
        Client client = saveClient();
        createKeys(client, existingKeys);

        String key = apiKeyService.create("new", client);

        assertAll(
                () -> assertNotNull(apiKeyService.findByKey(key).orElse(null)),
                () -> assertEquals(existingKeys + 1, apiKeyRepository.countByClientId(client.getId()))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"new", "production"})
    @DisplayName("При 10 ключах у клиента новый ключ не должен создаваться.")
    void create_shouldThrowWhenLimitReached(String name) {
        Client client = saveClient();
        createKeys(client, ApiKeyService.MAX_API_KEYS_PER_CLIENT);

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> apiKeyService.create(name, client));

        assertAll(
                () -> assertEquals("Достигнут лимит в 10 апи ключей. Удалите ненужный, чтобы создать новый.",
                        exception.getMessage()),
                () -> assertEquals(ApiKeyService.MAX_API_KEYS_PER_CLIENT,
                        apiKeyRepository.countByClientId(client.getId()))
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 10})
    @DisplayName("Ключи другого клиента не должны учитываться в лимите.")
    void create_shouldNotCountOtherClientKeys(int otherClientKeys) {
        Client other = saveClient();
        createKeys(other, otherClientKeys);
        Client client = saveClient();
        createKeys(client, ApiKeyService.MAX_API_KEYS_PER_CLIENT - 1);

        apiKeyService.create("new", client);

        assertEquals(ApiKeyService.MAX_API_KEYS_PER_CLIENT, apiKeyRepository.countByClientId(client.getId()));
    }

    @ParameterizedTest
    @ValueSource(ints = {12, 20})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("При параллельном создании у клиента не должно появиться больше 10 ключей.")
    void create_shouldNotExceedLimitConcurrently(int threads) throws InterruptedException {
        Client client = saveClient();
        try {
            List<String> errors = runConcurrently(threads, i -> apiKeyService.create("key_" + i, client));

            assertAll(
                    () -> assertEquals(ApiKeyService.MAX_API_KEYS_PER_CLIENT,
                            apiKeyRepository.countByClientId(client.getId())),
                    () -> assertEquals(threads - ApiKeyService.MAX_API_KEYS_PER_CLIENT, errors.size()),
                    () -> assertTrue(errors.stream().allMatch(
                            "Достигнут лимит в 10 апи ключей. Удалите ненужный, чтобы создать новый."::equals))
            );
        } finally {
            clientRepository.deleteById(client.getId());
        }
    }

    private void createKeys(Client client, int count) {
        for (int i = 0; i < count; i++) {
            apiKeyService.create("key_" + i, client);
        }
    }

    private Client saveClient() {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setUsername("test_" + client.getId());
        client.setRegisteredAt(Instant.now());
        client.setBalance(0);
        return clientRepository.save(client);
    }

    private List<String> runConcurrently(int threads, IntConsumer task) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    task.accept(index);
                    return null;
                }));
            }
            start.countDown();
        }
        List<String> errors = new ArrayList<>();
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                errors.add(e.getCause().getMessage());
            }
        }
        return errors;
    }
}
