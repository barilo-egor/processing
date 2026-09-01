package net.rcetech.domain.service.clients;

import jakarta.persistence.EntityManager;
import net.rcetech.domain.mapping.clients.ClientMapper;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.meta.clients.ClientStatus;
import net.rcetech.meta.clients.dto.ClientFilter;
import net.rcetech.meta.clients.dto.ClientResponseDTO;
import net.rcetech.meta.clients.dto.UpdateClientDTO;
import net.rcetech.meta.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ClientServiceTest {

    @TestConfiguration
    static class Configuration {
        @Bean
        public ClientMapper clientMapper() {
            return Mappers.getMapper(ClientMapper.class);
        }
    }

    @Container
    static MySQLContainer mySQLContainer = new MySQLContainer("mysql:8.0.46");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mySQLContainer::getUsername);
        registry.add("spring.datasource.password", mySQLContainer::getPassword);
    }

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ClientMapper clientMapper;

    @Autowired
    private EntityManager entityManager;

    private ClientService clientService;

    @BeforeEach
    void setUp() {
        clientService = new ClientService(clientRepository, clientMapper);
    }

    @ParameterizedTest
    @CsvSource({"""
            96826b27-4423-4494-a150-5b6a021963c5,10
            49048763-fb0c-4a73-a8b6-d8c9c471a3db,2
            """
    })
    @DisplayName("Метод должен найти клиента по id.")
    void findAll_ShouldFindById(UUID id, int pageSize) {
        Client client = new Client();
        client.setId(id);
        fillRequiredFields(client);
        clientRepository.save(client);
        for (int i = 0; i < 5; i++) {
            clientRepository.save(getDummyClient());
        }
        ClientFilter clientFilter = new ClientFilter(id, null, null, null, null);
        Page<ClientResponseDTO> actual = clientService.findAll(clientFilter, Pageable.ofSize(pageSize));
        assertAll(
                () -> assertEquals(1, actual.getTotalElements()),
                () -> assertEquals(id, actual.getContent().getFirst().id())
        );
    }

    @ParameterizedTest
    @CsvSource({"""
            superman,100
            dark_angel_1997,3
            """
    })
    @DisplayName("Метод должен найти клиента по username.")
    void findAll_ShouldFindByUsername(String username, int pageSize) {
        Client client = new Client();
        client.setUsername(username);
        fillRequiredFields(client);
        clientRepository.save(client);
        for (int i = 0; i < 5; i++) {
            clientRepository.save(getDummyClient());
        }
        ClientFilter clientFilter = new ClientFilter(null, username, null, null, null);
        Page<ClientResponseDTO> actual = clientService.findAll(clientFilter, Pageable.ofSize(pageSize));
        assertAll(
                () -> assertEquals(1, actual.getTotalElements()),
                () -> assertEquals(username, actual.getContent().getFirst().username())
        );
    }

    @DisplayName("Если для поиска передан пустой username, то он не должен учитываться при поиске.")
    @Test
    void findAll_shouldFindAllClientsIfUsernameBlank() {
        for (int i = 0; i < 10; i++) {
            Client client = new Client();
            fillRequiredFields(client);
            clientRepository.save(client);
        }
        Page<ClientResponseDTO> actual = clientService.findAll(
                new ClientFilter(null, "  ", null, null, null),
                Pageable.ofSize(10)
        );
        assertEquals(10, actual.getTotalElements());
    }

    @ParameterizedTest
    @CsvSource("""
            ACTIVE,1,5
            BLOCKED,4,10
            ACTIVE,0,1
            BLOCKED,10,10
            """)
    @DisplayName("Метод должен найти клиентов с соответствующим статусом.")
    void findAll_ShouldFindByClientStatus(ClientStatus clientStatus, int matchClientsSize, int notMatchClientsSize) {
        for (int i = 0; i < matchClientsSize; i++) {
            Client client = new Client();
            client.setStatus(clientStatus);
            fillRequiredFields(client);
            clientRepository.save(client);
        }
        for (int i = 0; i < notMatchClientsSize; i++) {
            Client client = new Client();
            for (ClientStatus status : ClientStatus.values()) {
                if (!status.equals(clientStatus)) {
                    client.setStatus(status);
                }
            }
            assertNotNull(client.getStatus());
            fillRequiredFields(client);
            clientRepository.save(client);
        }
        ClientFilter clientFilter = new ClientFilter(null, null, clientStatus, null, null);
        Page<ClientResponseDTO> actual = clientService.findAll(clientFilter, Pageable.ofSize(100));
        assertAll(
                () -> assertEquals(matchClientsSize, actual.getTotalElements()),
                () -> assertTrue(actual.getContent().stream().allMatch(c -> clientStatus.equals(c.status())))
        );
    }

    @ParameterizedTest
    @CsvSource({"""
            1787659000000,1,5
            1787658900000,5,10
            1787658500000,0,3
            1787658300000,6,6
            """})
    @DisplayName("Метод должен найти клиентов с указанной даты регистрации включительно.")
    void findAll_shouldFindByFrom(long fromMillis, int matchClientsSize, int notMatchClientsSize) {
        Instant from = Instant.ofEpochMilli(fromMillis);
        for (int i = 0; i < matchClientsSize; i++) {
            Client client = new Client();
            client.setRegisteredAt(from.plusSeconds(i * i * 100L));
            fillRequiredFields(client);
            clientRepository.save(client);
        }
        for (int i = 1; i <= notMatchClientsSize; i++) {
            Client client = new Client();
            client.setRegisteredAt(from.minusSeconds(i * i * 100L));
            fillRequiredFields(client);
            clientRepository.save(client);
        }
        ClientFilter clientFilter = new ClientFilter(null, null, null, from, null);
        Page<ClientResponseDTO> actual = clientService.findAll(clientFilter, Pageable.ofSize(100));
        assertAll(
                () -> assertEquals(matchClientsSize, actual.getTotalElements()),
                () -> assertTrue(actual.getContent().stream().allMatch(
                        c -> c.registeredAt().compareTo(from) >= 0
                ))
        );
    }

    @ParameterizedTest
    @CsvSource({"""
            1787659000000,1,5
            1787658900000,5,10
            1787658500000,0,3
            1787658300000,6,6
            """})
    @DisplayName("Метод должен найти клиентов до указанной даты регистрации.")
    void findAll_shouldFindByTo(long toMillis, int matchClientsSize, int notMatchClientsSize) {
        Instant to = Instant.ofEpochMilli(toMillis);
        for (int i = 1; i <= matchClientsSize; i++) {
            Client client = new Client();
            client.setRegisteredAt(to.minusSeconds(i * i * 100L));
            fillRequiredFields(client);
            clientRepository.save(client);
        }
        for (int i = 0; i < notMatchClientsSize; i++) {
            Client client = new Client();
            client.setRegisteredAt(to.plusSeconds(i * i * 100L));
            fillRequiredFields(client);
            clientRepository.save(client);
        }
        ClientFilter clientFilter = new ClientFilter(null, null, null, null, to);
        Page<ClientResponseDTO> actual = clientService.findAll(clientFilter, Pageable.ofSize(100));
        assertAll(
                () -> assertEquals(matchClientsSize, actual.getTotalElements()),
                () -> assertTrue(actual.getContent().stream().allMatch(
                        c -> c.registeredAt().compareTo(to) < 0
                ))
        );
    }

    @ParameterizedTest
    @CsvSource({"""
            1787659000000,1787659100000,1,5
            1787658900000,1787659000000,5,10
            1787658500000,1787658800000,0,3
            1787658300000,1787658450000,6,6
            """})
    @DisplayName("Метод должен найти клиентов по заданному диапазону даты регистрации, from включительно.")
    void findAll_ShouldFindByRegisteredAtRange(long fromMillis, long toMillis, int matchClientsSize, int notMatchClientsSize) {
        Instant from = Instant.ofEpochMilli(fromMillis);
        Instant to = Instant.ofEpochMilli(toMillis);
        for (int i = 0; i < matchClientsSize; i++) {
            Client client = new Client();
            client.setRegisteredAt(from.plusSeconds(i * 10L));
            fillRequiredFields(client);
            clientRepository.save(client);
        }
        for (int i = 1; i <= notMatchClientsSize; i++) {
            Client client = new Client();
            if (i % 2 == 0) {
                client.setRegisteredAt(from.minusSeconds(i * 10L));
            } else {
                client.setRegisteredAt(to.plusSeconds(i * 10L));
            }
            fillRequiredFields(client);
            clientRepository.save(client);
        }
        ClientFilter clientFilter = new ClientFilter(null, null, null, from, to);
        Page<ClientResponseDTO> actual = clientService.findAll(clientFilter, Pageable.ofSize(100));
        assertAll(
                () -> assertEquals(matchClientsSize, actual.getTotalElements()),
                () -> assertTrue(actual.getContent().stream().allMatch(
                        c -> c.registeredAt().compareTo(from) >= 0 && c.registeredAt().compareTo(to) < 0
                ))
        );
    }

    @DisplayName("Метод должен найти клиента, если в фильтре указать все его параметры.")
    @RepeatedTest(value = 3)
    void findAll_ShouldFindByAllFields() {
        Client client = new Client();
        UUID clientId = UUID.randomUUID();
        client.setId(clientId);
        fillRequiredFields(client);
        clientRepository.save(client);
        for (int i = 0; i < 5; i++) {
            Client dummy = new Client();
            fillRequiredFields(dummy);
            clientRepository.save(dummy);
        }
        Page<ClientResponseDTO> actual = clientService.findAll(
                new ClientFilter(client.getId(), client.getUsername(), client.getStatus(), client.getRegisteredAt(),
                        client.getRegisteredAt().plusMillis(1)),
                Pageable.ofSize(10)
        );
        assertAll(
                () -> assertEquals(1, actual.getTotalElements()),
                () -> assertEquals(client.getId(), actual.getContent().getFirst().id())
        );
    }

    Client getDummyClient() {
        Client client = new Client();
        fillRequiredFields(client);
        return client;
    }

    void fillRequiredFields(Client client) {
        if (Objects.isNull(client.getId())) {
            client.setId(UUID.randomUUID());
        }
        if (Objects.isNull(client.getUsername())) {
            client.setUsername("test_" + client.getId());
        }
        if (Objects.isNull(client.getRegisteredAt())) {
            client.setRegisteredAt(Instant.now());
        }
        if (Objects.isNull(client.getStatus())) {
            client.setStatus(ClientStatus.ACTIVE);
        }
    }

    @CsvSource("""
            50,5,4
            20,10,0
            """)
    @ParameterizedTest
    @DisplayName("Метод должен вернуть страницу соответственно указанным параметрам размера страницы и номера.")
    void findAll_shouldReturnPage(int allClientsSize, int pageSize, int pageNumber) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Order.asc("registeredAt")));
        for (int i = 0; i < allClientsSize; i++) {
            Client client = new Client();
            client.setUsername("test_" + i);
            client.setRegisteredAt(Instant.ofEpochMilli(1787659000000L + i));
            fillRequiredFields(client);
            clientRepository.save(client);
        }
        Page<ClientResponseDTO> actual = clientService.findAll(new ClientFilter(null, null, null, null, null), pageable);
        assertEquals(allClientsSize, actual.getTotalElements());
        int fromIndex = pageNumber * pageSize;
        for (ClientResponseDTO c : actual.getContent()) {
            String expectedUsername = "test_" + fromIndex;
            assertEquals(expectedUsername, c.username());
            fromIndex++;
        }
    }

    @Test
    @DisplayName("Метод должен найти всех клиентов если передан null в качестве фильтра.")
    void findAll_shouldReturnAllClientsIfFilterIsNull() {
        for (int i = 0; i < 10; i++) {
            Client client = new Client();
            fillRequiredFields(client);
            clientRepository.save(client);
        }
        Page<ClientResponseDTO> actual = clientService.findAll(null, Pageable.ofSize(10));
        assertEquals(10, actual.getTotalElements());
    }

    @ValueSource(strings = {
            "185975b3-094a-43fb-a1a5-7128c39b39ab",
            "c44cc597-7142-4636-b5f6-799d352fd631"
    })
    @ParameterizedTest
    @DisplayName("Метод должен выбросить исключение, если клиент по переданному id не найден.")
    void update_shouldThrowBadRequestIfClientNotFound(UUID id) {
        for (int i = 0; i < 5; i++) {
            clientRepository.save(getDummyClient());
        }
        UpdateClientDTO updateClientDTO = new UpdateClientDTO(null, null, null, null);
        assertThrows(BadRequestException.class,
                () -> clientService.update(id, updateClientDTO));
    }

    @Test
    @DisplayName("Метод не должен обновлять поля, если в DTO они null.")
    void update_shouldNotUpdateFieldsIfNull() {
        UpdateClientDTO updateClientDTO = new UpdateClientDTO(null, null, null, null);
        Client client = new Client();
        UUID clientId = UUID.randomUUID();
        client.setId(clientId);
        fillRequiredFields(client);
        clientRepository.save(client);
        clientService.update(clientId, updateClientDTO);
        Client updated = clientRepository.findById(clientId).orElseThrow(IllegalStateException::new);
        assertAll(
                () -> assertNotNull(updated.getStatus()),
                () -> assertNotNull(updated.getOrderTimeoutSeconds())
        );
    }

    @CsvSource("""
            BLOCKED,25.0,500,https://example.com/callback
            ACTIVE,13.5,1200,https://google.com/webhook
            """)
    @ParameterizedTest
    @DisplayName("Метод должен обновить все переданные поля.")
    void update_shouldUpdateClient(ClientStatus status, BigDecimal commissionPercent, Integer orderTimeoutSeconds,
                                   String callbackUrl) {
        assertNotEquals(Client.DEFAULT_ORDER_TIMEOUT, orderTimeoutSeconds,
                "Для теста нужно отличное от дефолтного значение времени таймаута ордера.");
        UUID id = UUID.randomUUID();
        Client client = new Client();
        client.setId(id);
        for (ClientStatus clientStatus : ClientStatus.values()) {
            if (!clientStatus.equals(status)) {
                client.setStatus(clientStatus);
                break;
            }
        }
        assertNotNull(client.getStatus(),
                "Необходим установленный статус клиента, отличный от полученного в параметре.");
        fillRequiredFields(client);
        clientRepository.save(client);
        UpdateClientDTO updateClientDTO = new UpdateClientDTO(status, commissionPercent, orderTimeoutSeconds, callbackUrl);
        clientService.update(id, updateClientDTO);
        Client updated = clientRepository.findById(id).orElseThrow(IllegalStateException::new);
        assertAll(
                () -> assertEquals(status, updated.getStatus()),
                () -> assertEquals(commissionPercent, updated.getCommissionPercent()),
                () -> assertEquals(orderTimeoutSeconds, updated.getOrderTimeoutSeconds()),
                () -> assertEquals(callbackUrl, updated.getCallbackUrl())
        );
    }
}