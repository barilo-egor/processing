package net.rcetech.domain.service.clients;

import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.repository.clients.IpAddressRepository;
import net.rcetech.domain.test.SharedMySQLContainer;
import net.rcetech.meta.clients.dto.IpAddressResponseDTO;
import net.rcetech.meta.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(IpAddressService.class)
class IpAddressServiceTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        SharedMySQLContainer.register(registry);
    }

    @Autowired
    private IpAddressRepository ipAddressRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private IpAddressService ipAddressService;

    @ParameterizedTest
    @ValueSource(ints = {0, 4, 9})
    @DisplayName("Адрес должен добавляться, пока у клиента меньше 10 адресов.")
    void create_shouldCreateAddressBelowLimit(int existingAddresses) {
        Client client = saveClient();
        createAddresses(client, existingAddresses);

        IpAddressResponseDTO created = ipAddressService.create("192.168.1.1", client);

        assertAll(
                () -> assertNotNull(created.id()),
                () -> assertEquals("192.168.1.1", created.address()),
                () -> assertTrue(ipAddressRepository.existsByIdAndClientId(created.id(), client.getId())),
                () -> assertEquals(existingAddresses + 1, ipAddressRepository.countByClientId(client.getId()))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"192.168.1.1", "2001:db8::1"})
    @DisplayName("При 10 адресах у клиента новый адрес не должен добавляться.")
    void create_shouldThrowWhenLimitReached(String address) {
        Client client = saveClient();
        createAddresses(client, IpAddressService.MAX_IP_ADDRESSES_PER_CLIENT);

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> ipAddressService.create(address, client));

        assertAll(
                () -> assertEquals("Достигнут лимит в 10 IP-адресов. Удалите ненужный, чтобы добавить новый.",
                        exception.getMessage()),
                () -> assertEquals(IpAddressService.MAX_IP_ADDRESSES_PER_CLIENT,
                        ipAddressRepository.countByClientId(client.getId()))
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 10})
    @DisplayName("Адреса другого клиента не должны учитываться в лимите.")
    void create_shouldNotCountOtherClientAddresses(int otherClientAddresses) {
        Client other = saveClient();
        createAddresses(other, otherClientAddresses);
        Client client = saveClient();
        createAddresses(client, IpAddressService.MAX_IP_ADDRESSES_PER_CLIENT - 1);

        ipAddressService.create("192.168.1.1", client);

        assertEquals(IpAddressService.MAX_IP_ADDRESSES_PER_CLIENT,
                ipAddressRepository.countByClientId(client.getId()));
    }

    @ParameterizedTest
    @CsvSource({
            "192.168.1.1,192.168.1.1",
            "' 10.0.0.255 ',10.0.0.255",
            "::1,0:0:0:0:0:0:0:1",
            "2001:DB8::A,2001:db8:0:0:0:0:0:a",
            "::ffff:172.16.0.1,172.16.0.1"
    })
    @DisplayName("Адрес должен сохраняться в нормализованном виде.")
    void create_shouldNormalizeAddress(String address, String expected) {
        Client client = saveClient();

        IpAddressResponseDTO created = ipAddressService.create(address, client);

        assertAll(
                () -> assertEquals(expected, created.address()),
                () -> assertEquals(expected, ipAddressRepository.findById(created.id()).orElseThrow().getAddress())
        );
    }

    @ParameterizedTest
    @CsvSource({
            "192.168.1.1,192.168.1.1",
            "::1,0:0:0:0:0:0:0:1",
            "2001:db8::1,2001:DB8:0:0:0:0:0:1"
    })
    @DisplayName("Повторное добавление того же адреса должно отклоняться.")
    void create_shouldThrowWhenAddressExists(String first, String second) {
        Client client = saveClient();
        IpAddressResponseDTO created = ipAddressService.create(first, client);

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> ipAddressService.create(second, client));

        assertAll(
                () -> assertEquals("IP-адрес " + created.address() + " уже добавлен.", exception.getMessage()),
                () -> assertEquals(1, ipAddressRepository.countByClientId(client.getId()))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"192.168.1.1", "2001:db8::1"})
    @DisplayName("Один и тот же адрес должен добавляться разным клиентам.")
    void create_shouldAllowSameAddressForDifferentClients(String address) {
        Client first = saveClient();
        Client second = saveClient();

        ipAddressService.create(address, first);
        ipAddressService.create(address, second);

        assertAll(
                () -> assertEquals(1, ipAddressRepository.countByClientId(first.getId())),
                () -> assertEquals(1, ipAddressRepository.countByClientId(second.getId()))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"256.1.1.1", "192.168.1", "192.168.01.1", "1", "localhost", "example.com",
            "abc", "2001:db8::g", ":", "1:2:3:4:5:6:7:8:9", "[::1]", "fe80::1%eth0", "192.168.1.1/24"})
    @DisplayName("Некорректный адрес должен отклоняться.")
    void create_shouldThrowWhenAddressInvalid(String address) {
        Client client = saveClient();

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> ipAddressService.create(address, client));

        assertAll(
                () -> assertEquals("Некорректный IP-адрес " + address + ".", exception.getMessage()),
                () -> assertEquals(0, ipAddressRepository.countByClientId(client.getId()))
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    @DisplayName("Метод должен вернуть только адреса клиента в порядке добавления.")
    void findAllByClientId_shouldReturnOnlyClientAddresses(int count) {
        Client client = saveClient();
        createAddresses(client, count);
        createAddresses(saveClient(), 2);

        List<IpAddressResponseDTO> addresses = ipAddressService.findAllByClientId(client);

        assertEquals(createdAddresses(count), addresses.stream().map(IpAddressResponseDTO::address).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"192.168.1.1", "2001:db8::1"})
    @DisplayName("Клиент должен удалять свой адрес.")
    void delete_shouldDeleteOwnAddress(String address) {
        Client client = saveClient();
        IpAddressResponseDTO created = ipAddressService.create(address, client);

        ipAddressService.delete(client, created.id());

        assertFalse(ipAddressRepository.existsById(created.id()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"192.168.1.1", "2001:db8::1"})
    @DisplayName("Клиент не должен удалять чужой адрес.")
    void delete_shouldThrowForOtherClientAddress(String address) {
        Client owner = saveClient();
        IpAddressResponseDTO created = ipAddressService.create(address, owner);
        Client client = saveClient();

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> ipAddressService.delete(client, created.id()));

        assertAll(
                () -> assertEquals("Запись с идентификатором " + created.id() + " не найдена.",
                        exception.getMessage()),
                () -> assertTrue(ipAddressRepository.existsById(created.id()))
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {12, 20})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("При параллельном добавлении у клиента не должно появиться больше 10 IP-адресов.")
    void create_shouldNotExceedLimitConcurrently(int threads) throws InterruptedException {
        Client client = saveClient();
        try {
            List<String> errors = runConcurrently(threads, i -> ipAddressService.create("10.0.0." + i, client));

            assertAll(
                    () -> assertEquals(IpAddressService.MAX_IP_ADDRESSES_PER_CLIENT,
                            ipAddressRepository.countByClientId(client.getId())),
                    () -> assertEquals(threads - IpAddressService.MAX_IP_ADDRESSES_PER_CLIENT, errors.size()),
                    () -> assertTrue(errors.stream().allMatch(
                            "Достигнут лимит в 10 IP-адресов. Удалите ненужный, чтобы добавить новый."::equals))
            );
        } finally {
            clientRepository.deleteById(client.getId());
        }
    }

    @ParameterizedTest
    @CsvSource({
            "10.0.0.1, 10.0.0.1",
            "2001:db8::1, 2001:0db8:0000:0000:0000:0000:0000:0001",
            "::1, 0:0:0:0:0:0:0:1"
    })
    @DisplayName("Запрос с адреса из белого списка должен быть разрешён в любой записи адреса.")
    void isAllowed_shouldAllowListedAddress(String listed, String remoteAddress) {
        Client client = saveClient();
        ipAddressService.create(listed, client);

        assertTrue(ipAddressService.isAllowed(client, remoteAddress));
    }

    @ParameterizedTest
    @CsvSource({
            "10.0.0.1, 10.0.0.2",
            "2001:db8::1, 2001:db8::2",
            "10.0.0.1, not-an-ip"
    })
    @DisplayName("Запрос с адреса не из белого списка должен быть запрещён.")
    void isAllowed_shouldDenyUnlistedAddress(String listed, String remoteAddress) {
        Client client = saveClient();
        ipAddressService.create(listed, client);

        assertFalse(ipAddressService.isAllowed(client, remoteAddress));
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.0.0.1", "2001:db8::1"})
    @DisplayName("При пустом белом списке запрос должен быть запрещён.")
    void isAllowed_shouldDenyWhenListEmpty(String remoteAddress) {
        assertFalse(ipAddressService.isAllowed(saveClient(), remoteAddress));
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.0.0.1", "2001:db8::1"})
    @DisplayName("Адрес из белого списка другого клиента не должен разрешать запрос.")
    void isAllowed_shouldDenyAddressOfOtherClient(String address) {
        Client client = saveClient();
        ipAddressService.create(address, saveClient());

        assertFalse(ipAddressService.isAllowed(client, address));
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.0.0.1", "2001:db8::1"})
    @DisplayName("При выключенном ограничении запрос должен быть разрешён с любого адреса.")
    void isAllowed_shouldAllowAnyAddressWhenRestrictionDisabled(String remoteAddress) {
        Client client = saveClient();
        client.setIpRestrictionEnabled(false);
        clientRepository.save(client);

        assertTrue(ipAddressService.isAllowed(client, remoteAddress));
    }

    private void createAddresses(Client client, int count) {
        createdAddresses(count).forEach(address -> ipAddressService.create(address, client));
    }

    private List<String> createdAddresses(int count) {
        return IntStream.range(0, count).mapToObj(i -> "10.0.0." + i).toList();
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
