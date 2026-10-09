package net.rcetech.domain.service.clients;

import jakarta.validation.ConstraintViolationException;
import net.rcetech.domain.mapping.clients.ClientMapper;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.meta.clients.dto.ClientFilter;
import net.rcetech.meta.clients.dto.ClientUpdateRequest;
import net.rcetech.meta.clients.dto.UpdateClientDTO;
import net.rcetech.meta.clients.projection.SupportClientProjection;
import net.rcetech.meta.exception.BadRequestException;
import net.rcetech.meta.orders.RequestMethod;
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
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(ClientService.class)
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
    private ClientService clientService;

    @Test
    @DisplayName("Клиент должен быть сохранен с дефолтными значениями.")
    void save_shouldSaveWithDefaultValues() {
        Client dummyClient = getDummyClient();
        Client actual = clientService.save(dummyClient);
        assertEquals(0, actual.getBalance());
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
        ClientFilter clientFilter = new ClientFilter(id, null, null, null);
        Page<SupportClientProjection> actual = clientService.findAll(clientFilter, Pageable.ofSize(pageSize), SupportClientProjection.class);
        assertAll(
                () -> assertEquals(1, actual.getTotalElements()),
                () -> assertEquals(id, actual.getContent().getFirst().getId())
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
        ClientFilter clientFilter = new ClientFilter(null, username, null, null);
        Page<SupportClientProjection> actual = clientService.findAll(clientFilter, Pageable.ofSize(pageSize), SupportClientProjection.class);
        assertAll(
                () -> assertEquals(1, actual.getTotalElements()),
                () -> assertEquals(username, actual.getContent().getFirst().getUsername())
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
        Page<SupportClientProjection> actual = clientService.findAll(
                new ClientFilter(null, "  ", null, null),
                Pageable.ofSize(10), SupportClientProjection.class
        );
        assertEquals(10, actual.getTotalElements());
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
        ClientFilter clientFilter = new ClientFilter(null, null, from, null);
        Page<SupportClientProjection> actual = clientService.findAll(clientFilter, Pageable.ofSize(100), SupportClientProjection.class);
        assertAll(
                () -> assertEquals(matchClientsSize, actual.getTotalElements()),
                () -> assertTrue(actual.getContent().stream().allMatch(
                        c -> c.getRegisteredAt().compareTo(from) >= 0
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
        ClientFilter clientFilter = new ClientFilter(null, null, null, to);
        Page<SupportClientProjection> actual = clientService.findAll(clientFilter, Pageable.ofSize(100), SupportClientProjection.class);
        assertAll(
                () -> assertEquals(matchClientsSize, actual.getTotalElements()),
                () -> assertTrue(actual.getContent().stream().allMatch(
                        c -> c.getRegisteredAt().compareTo(to) < 0
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
            SortedSet<RequestMethod> sortedSet = new TreeSet<>();
            sortedSet.add(RequestMethod.CARD);
            sortedSet.add(RequestMethod.SBP);
            client.setMethods(sortedSet);
            fillRequiredFields(client);
            clientRepository.save(client);
        }
        ClientFilter clientFilter = new ClientFilter(null, null, from, to);
        Page<SupportClientProjection> actual = clientService.findAll(clientFilter, Pageable.ofSize(100), SupportClientProjection.class);
        assertAll(
                () -> assertEquals(matchClientsSize, actual.getTotalElements()),
                () -> assertTrue(actual.getContent().stream().allMatch(
                        c -> c.getRegisteredAt().compareTo(from) >= 0 && c.getRegisteredAt().compareTo(to) < 0
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
        Page<SupportClientProjection> actual = clientService.findAll(
                new ClientFilter(client.getId(), client.getUsername(), client.getRegisteredAt().minusSeconds(5),
                        client.getRegisteredAt().plusSeconds(5)),
                Pageable.ofSize(10), SupportClientProjection.class
        );
        assertAll(
                () -> assertEquals(1, actual.getTotalElements()),
                () -> assertEquals(client.getId(), actual.getContent().getFirst().getId())
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
        if (Objects.isNull(client.getBalance())) {
            client.setBalance(0);
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
        Page<SupportClientProjection> actual = clientService.findAll(null, Pageable.ofSize(10), SupportClientProjection.class);
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
        UpdateClientDTO updateClientDTO = new UpdateClientDTO(
                null, null, null, null, null
        );
        assertThrows(BadRequestException.class,
                () -> clientService.update(id, updateClientDTO));
    }

    @Test
    @DisplayName("Метод не должен обновлять поля, если в DTO они null.")
    void update_shouldNotUpdateFieldsIfNull() {
        UpdateClientDTO updateClientDTO = new UpdateClientDTO(
                null, null, null, null, null
        );
        Client client = new Client();
        UUID clientId = UUID.randomUUID();
        client.setId(clientId);
        fillRequiredFields(client);
        client.setComment("comment");
        client.setMinWithdrawalAmount(1000);
        clientRepository.save(client);
        clientService.update(clientId, updateClientDTO);
        Client updated = clientRepository.findById(clientId).orElseThrow(IllegalStateException::new);
        assertAll(
                () -> assertNull(updated.getCommissionPercent()),
                () -> assertEquals(900, updated.getOrderTimeoutSeconds()),
                () -> assertNull(updated.getCallbackUrl()),
                () -> assertTrue(updated.getMethods().isEmpty()),
                () -> assertEquals("comment", client.getComment()),
                () -> assertEquals(1000, client.getMinWithdrawalAmount())
        );
    }

    @CsvSource("""
            25.0,500,CARD;SBP,qwerty,50000
            13.5,1200,CARD,qwerty qwerty,1000
            """)
    @ParameterizedTest
    @DisplayName("Метод должен обновить все переданные поля.")
    void update_shouldUpdateClient(BigDecimal commissionPercent, Integer orderTimeoutSeconds,
                                   String methodsString, String comment, Integer minWithdrawalAmount) {
        assertNotEquals(Client.DEFAULT_ORDER_TIMEOUT, orderTimeoutSeconds,
                "Для теста нужно отличное от дефолтного значение времени таймаута ордера.");
        UUID id = UUID.randomUUID();
        Client client = new Client();
        client.setId(id);
        fillRequiredFields(client);
        clientRepository.save(client);
        Set<RequestMethod> methods = Arrays.stream(methodsString.split(";"))
                .map(RequestMethod::valueOf)
                .collect(Collectors.toSet());
        UpdateClientDTO updateClientDTO = new UpdateClientDTO(
                commissionPercent, orderTimeoutSeconds, methods, comment, minWithdrawalAmount
        );
        clientService.update(id, updateClientDTO);
        Client updated = clientRepository.findById(id).orElseThrow(IllegalStateException::new);
        assertAll(
                () -> assertEquals(commissionPercent, updated.getCommissionPercent()),
                () -> assertEquals(orderTimeoutSeconds, updated.getOrderTimeoutSeconds()),
                () -> assertEquals(methods, updated.getMethods()),
                () -> assertEquals(comment, updated.getComment()),
                () -> assertEquals(minWithdrawalAmount, updated.getMinWithdrawalAmount())
        );
    }

    @Test
    @DisplayName("Должен быть брошен ConstraintViolationException, если баланс меньше нуля.")
    void saveClient_shouldThrowExceptionIfBalanceNotPositive() {
        Client client = new Client();
        client.setBalance(-1);
        fillRequiredFields(client);
        assertThrows(ConstraintViolationException.class, () -> clientRepository.saveAndFlush(client));
    }

    @Test
    @DisplayName("Метод должен удалить привязанные методы, если передан пустой сет.")
    void update_shouldSetToEmptyIfPassedEmptyMethodsSet() {
        Client client = new Client();
        fillRequiredFields(client);
        SortedSet<RequestMethod> methods = new TreeSet<>(Arrays.asList(RequestMethod.values()));
        client.setMethods(methods);
        clientRepository.save(client);
        assertNotNull(client.getId());

        clientService.update(client.getId(), new UpdateClientDTO(
                null, null, Set.of(), null, null)
        );
        Client updated = clientRepository.findById(client.getId()).orElseThrow(IllegalStateException::new);
        assertTrue(updated.getMethods().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://google.com/callback",
            "https://yandex.com/callback"
    })
    @DisplayName("Метод должен обновить КБ юрл.")
    void update_shouldUpdateCallbackUrl(String url) {
        Client client = new Client();
        client.setCallbackUrl("https://example.com");
        fillRequiredFields(client);
        clientRepository.save(client);
        assertNotNull(client.getId());

        clientService.update(client.getId(), new ClientUpdateRequest(url));
        Client updated = clientRepository.findById(client.getId()).orElseThrow(IllegalStateException::new);
        assertEquals(url, updated.getCallbackUrl());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://google.com/callback",
            "https://yandex.com/callback"
    })
    @DisplayName("Метод не должен обновлять юрл, если передан null.")
    void update_shouldNotUpdateCallbackUrlIfNullPassed(String url) {
        Client client = new Client();
        client.setCallbackUrl(url);
        fillRequiredFields(client);
        clientRepository.save(client);
        assertNotNull(client.getId());

        clientService.update(client.getId(), new ClientUpdateRequest(null));
        Client updated = clientRepository.findById(client.getId()).orElseThrow(IllegalStateException::new);
        assertEquals(url, updated.getCallbackUrl());
    }

    @ParameterizedTest
    @CsvSource({
            "f3f0d1d5-f4c6-4af5-ad9c-327ac365ea92,test1",
            "80adcfbe-d80d-432a-82d3-b61bcc7aacdd,smokilolik"
    })
    @DisplayName("Метод не должен создавать клиента, если клиент с переданным id уже существует.")
    void createIfNotExists_shouldNotCreateClientIfExists(UUID id, String username) {
        Client client = new Client();
        client.setId(id);
        client.setUsername(username);
        client.setRegisteredAt(Instant.now());
        clientRepository.save(client);

        clientService.createIfNotExists(id, username);

        assertEquals(1, clientRepository.count());
    }

    @ParameterizedTest
    @CsvSource({
            "f3f0d1d5-f4c6-4af5-ad9c-327ac365ea92,test1",
            "80adcfbe-d80d-432a-82d3-b61bcc7aacdd,smokilolik"
    })
    @DisplayName("Метод должен создать клиента, если клиент с переданным id не существует.")
    void createIfNotExists_shouldCreateClientIfNotExists(UUID id, String username) {
        assertEquals(0, clientRepository.count());

        clientService.createIfNotExists(id, username);

        List<Client> actual = clientRepository.findAll();
        assertEquals(1, actual.size());
        assertEquals(id, actual.getFirst().getId());
        assertEquals(username, actual.getFirst().getUsername());
    }
}