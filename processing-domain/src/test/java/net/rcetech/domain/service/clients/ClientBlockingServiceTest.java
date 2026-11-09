package net.rcetech.domain.service.clients;

import net.rcetech.domain.mapping.clients.ClientMapper;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.test.SharedMySQLContainer;
import net.rcetech.meta.clients.ClientBlockChangedEvent;
import net.rcetech.meta.clients.projection.SupportClientProjection;
import net.rcetech.meta.exception.BadRequestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@RecordApplicationEvents
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ClientBlockingService.class, ClientService.class})
class ClientBlockingServiceTest {

    @TestConfiguration
    static class Configuration {
        @Bean
        public ClientMapper clientMapper() {
            return Mappers.getMapper(ClientMapper.class);
        }

        @Bean
        public FailingListener failingListener() {
            return new FailingListener();
        }
    }

    static class FailingListener {

        private boolean failing;

        @EventListener
        public void blockChanged(ClientBlockChangedEvent event) {
            if (failing) {
                throw new IllegalStateException("Keycloak недоступен");
            }
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        SharedMySQLContainer.register(registry);
    }

    @Autowired
    private ClientBlockingService clientBlockingService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private FailingListener failingListener;

    @Autowired
    private ApplicationEvents applicationEvents;

    private final List<UUID> clientIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        failingListener.failing = false;
        clientRepository.deleteAllById(clientIds);
    }

    @Test
    @DisplayName("Метод должен заполнить момент блокировки и опубликовать событие блокировки.")
    void block_shouldSetBlockedAtAndPublishEvent() {
        Client client = saveClient(null);
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        SupportClientProjection result = clientBlockingService.block(client.getId());

        Instant blockedAt = blockedAt(client);
        assertAll(
                () -> assertNotNull(blockedAt),
                () -> assertFalse(blockedAt.isBefore(before)),
                () -> assertEquals(blockedAt, result.getBlockedAt()),
                () -> assertEquals(List.of(new ClientBlockChangedEvent(client.getId(), client.getUsername(), true)),
                        applicationEvents.stream(ClientBlockChangedEvent.class).toList())
        );
    }

    @Test
    @DisplayName("Метод не должен менять момент блокировки уже заблокированного клиента, но должен повторить событие.")
    void block_shouldKeepBlockedAtIfAlreadyBlocked() {
        Instant blockedAt = Instant.parse("2026-11-01T10:15:30Z");
        Client client = saveClient(blockedAt);

        SupportClientProjection result = clientBlockingService.block(client.getId());

        assertAll(
                () -> assertEquals(blockedAt, blockedAt(client)),
                () -> assertEquals(blockedAt, result.getBlockedAt()),
                () -> assertEquals(List.of(new ClientBlockChangedEvent(client.getId(), client.getUsername(), true)),
                        applicationEvents.stream(ClientBlockChangedEvent.class).toList())
        );
    }

    @Test
    @DisplayName("Метод должен сохранить блокировку, если обработчик события блокировки выбросил исключение.")
    void block_shouldKeepBlockedAtIfListenerFailed() {
        Client client = saveClient(null);
        failingListener.failing = true;

        assertThrows(IllegalStateException.class, () -> clientBlockingService.block(client.getId()));

        assertNotNull(blockedAt(client));
    }

    @ValueSource(strings = {"2026-11-01T10:15:30Z", ""})
    @ParameterizedTest
    @DisplayName("Метод должен очистить момент блокировки и опубликовать событие разблокировки.")
    void unblock_shouldClearBlockedAtAndPublishEvent(String blockedAt) {
        Client client = saveClient(blockedAt.isEmpty() ? null : Instant.parse(blockedAt));

        SupportClientProjection result = clientBlockingService.unblock(client.getId());

        assertAll(
                () -> assertNull(blockedAt(client)),
                () -> assertNull(result.getBlockedAt()),
                () -> assertEquals(List.of(new ClientBlockChangedEvent(client.getId(), client.getUsername(), false)),
                        applicationEvents.stream(ClientBlockChangedEvent.class).toList())
        );
    }

    @Test
    @DisplayName("Метод должен оставить клиента заблокированным, если обработчик события разблокировки выбросил исключение.")
    void unblock_shouldKeepBlockedAtIfListenerFailed() {
        Instant blockedAt = Instant.parse("2026-11-01T10:15:30Z");
        Client client = saveClient(blockedAt);
        failingListener.failing = true;

        assertThrows(IllegalStateException.class, () -> clientBlockingService.unblock(client.getId()));

        assertEquals(blockedAt, blockedAt(client));
    }

    @Test
    @DisplayName("Методы должны выбросить BadRequestException и не публиковать событие, если клиента нет.")
    void blockAndUnblock_shouldThrowIfClientNotFound() {
        UUID id = UUID.randomUUID();

        assertAll(
                () -> assertEquals("Клиент не найден.",
                        assertThrows(BadRequestException.class, () -> clientBlockingService.block(id)).getMessage()),
                () -> assertEquals("Клиент не найден.",
                        assertThrows(BadRequestException.class, () -> clientBlockingService.unblock(id)).getMessage()),
                () -> assertEquals(0, applicationEvents.stream(ClientBlockChangedEvent.class).count())
        );
    }

    private Client saveClient(Instant blockedAt) {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setUsername("test_" + client.getId());
        client.setRegisteredAt(Instant.now());
        client.setBlockedAt(blockedAt);
        clientIds.add(client.getId());
        return clientRepository.saveAndFlush(client);
    }

    private Instant blockedAt(Client client) {
        return clientRepository.findById(client.getId()).orElseThrow().getBlockedAt();
    }
}
