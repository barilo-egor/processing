package net.rcetech.domain.service.billing;

import net.rcetech.domain.mapping.clients.ClientMapper;
import net.rcetech.domain.model.billing.WithdrawalRequest;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.repository.billing.WithdrawalRequestRepository;
import net.rcetech.domain.repository.billing.WithdrawalRequestSpecifications;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.meta.billing.WithdrawalRequestStatus;
import net.rcetech.meta.billing.dto.ClientWithdrawalRequestFilter;
import net.rcetech.meta.billing.dto.ClientWithdrawalRequestResponse;
import net.rcetech.meta.clients.ClientStatus;
import net.rcetech.meta.exception.BadRequestException;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.exception.ServiceUnavailableException;
import net.rcetech.rates.RatesConsumer;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ClientService.class, WithdrawalRequestService.class})
class WithdrawalRequestServiceTest {

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

    @MockitoBean
    private RatesConsumer ratesConsumer;

    @Autowired
    private WithdrawalRequestService withdrawalRequestService;

    @Autowired
    private WithdrawalRequestRepository withdrawalRequestRepository;

    @Autowired
    private ClientRepository clientRepository;

    Client getDummyClient(BigDecimal commissionPercent) {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setUsername("test" + client.getId());
        client.setRegisteredAt(Instant.now());
        client.setStatus(ClientStatus.ACTIVE);
        client.setCommissionPercent(commissionPercent);
        return clientRepository.save(client);
    }

    void fillFields(WithdrawalRequest withdrawalRequest) {
        if (Objects.isNull(withdrawalRequest.getId())) {
            withdrawalRequest.setId(UUID.randomUUID());
        }
        if (Objects.isNull(withdrawalRequest.getClient())) {
            withdrawalRequest.setClient(getDummyClient(BigDecimal.ONE));
        }
        if (Objects.isNull(withdrawalRequest.getStatus())) {
            withdrawalRequest.setStatus(WithdrawalRequestStatus.NEW);
        }
        if (Objects.isNull(withdrawalRequest.getCreatedAt())) {
            withdrawalRequest.setCreatedAt(Instant.now());
        }
        if (Objects.isNull(withdrawalRequest.getGrossSourceAmount())) {
            withdrawalRequest.setGrossSourceAmount(10000);
        }
        if (Objects.isNull(withdrawalRequest.getCommissionPercent())) {
            withdrawalRequest.setCommissionPercent(BigDecimal.ONE);
        }
        if (Objects.isNull(withdrawalRequest.getNetSourceAmount())) {
            withdrawalRequest.setNetSourceAmount(9000);
        }
        if (Objects.isNull(withdrawalRequest.getRate())) {
            withdrawalRequest.setRate(new BigDecimal(80));
        }
        if (Objects.isNull(withdrawalRequest.getTargetAmount())) {
            withdrawalRequest.setTargetAmount(150);
        }
        if (Objects.isNull(withdrawalRequest.getAddress())) {
            withdrawalRequest.setAddress("TX9zFakeAddressTRC20usdtNotReal99x");
        }
    }

    @Test
    @DisplayName("Метод должен бросить BaseException, если клиент не найден.")
    void create_shouldThrowBaseExceptionIfClientNotFound() {
        UUID clientId = UUID.randomUUID();
        assertThrows(
                BaseException.class,
                () -> withdrawalRequestService.create(clientId, null, null)
        );
    }

    @Test
    @DisplayName("Метод должен бросить ServiceUnavailableException, если клиенту не установлен процент комиссии.")
    void create_shouldThrowServiceUnavailableExceptionIfClientCommissionPercentIsNull() {
        Client client = getDummyClient(null);
        UUID clientId = client.getId();
        assertThrows(
                ServiceUnavailableException.class,
                () -> withdrawalRequestService.create(clientId, null, null)
        );
    }

    @ParameterizedTest
    @CsvSource({
            "5357,TX9zFakeAddressTRC20usdtNotReal99x,70,15.5,4527,65",
            "85330,TF7y6fakeTronAddressUSDT88888xxxx1,80,21,67411,843",
            "2450,TF7y6fakeTronAddressUSDT88888xxxx1,74.5,14.4,2097,28"
    })
    @DisplayName("Метод должен создать заявку.")
    void create_shouldCreateRequest(Integer amount, String address, BigDecimal rate, BigDecimal commissionPercent,
                                    Integer expectedGrossAmount, Integer expectedTargetAmount) {
        Client client = getDummyClient(commissionPercent);
        when(ratesConsumer.consume()).thenReturn(rate);

        withdrawalRequestService.create(client.getId(), amount, address);
        List<WithdrawalRequest> requests = withdrawalRequestRepository.findAll();

        assertEquals(1, requests.size());
        WithdrawalRequest actual = requests.getFirst();
        assertAll(
                () -> assertNotNull(actual.getId()),
                () -> assertEquals(client.getId(), actual.getClient().getId()),
                () -> assertEquals(WithdrawalRequestStatus.NEW, actual.getStatus()),
                () -> assertNotNull(actual.getCreatedAt()),
                () -> assertEquals(amount, actual.getNetSourceAmount()),
                () -> assertEquals(client.getCommissionPercent(), commissionPercent),
                () -> assertEquals(expectedGrossAmount, actual.getGrossSourceAmount()),
                () -> assertEquals(rate, actual.getRate()),
                () -> assertEquals(expectedTargetAmount, actual.getTargetAmount()),
                () -> assertEquals(address, actual.getAddress())
        );
    }

    @RepeatedTest(value = 2)
    @DisplayName("Метод должен вернуть заявки клиента.")
    void findAll_shouldReturnClientRequests() {
        when(ratesConsumer.consume()).thenReturn(new BigDecimal(80));
        Client client = getDummyClient(BigDecimal.ONE);
        for (int i = 0; i < 3; i++) {
            withdrawalRequestService.create(client.getId(), 1000 * (i + 1), "TX9zFakeAddressTRC20usdtNotReal99x");
        }
        Client targetClient = getDummyClient(BigDecimal.ONE);
        Set<UUID> expectedRequests = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            expectedRequests.add(
                    withdrawalRequestService.create(
                            targetClient.getId(), 1000 * (i + 1), "TX9zFakeAddressTRC20usdtNotReal99x"
                    ).getId()
            );
        }
        Page<ClientWithdrawalRequestResponse> actual = withdrawalRequestService.findAll(
                WithdrawalRequestSpecifications.matches(targetClient.getId(), null),
                Pageable.ofSize(20),
                ClientWithdrawalRequestResponse.class
        );
        assertEquals(3, actual.getTotalElements());
        assertTrue(actual.stream().allMatch(r -> expectedRequests.contains(r.id())));
    }

    @RepeatedTest(value = 2)
    @DisplayName("Метод должен вернуть заявку по id.")
    void findAll_shouldReturnRequestById() {
        Client client = getDummyClient(BigDecimal.ONE);
        when(ratesConsumer.consume()).thenReturn(new BigDecimal(80));
        withdrawalRequestService.create(client.getId(), 5000, "TX9zFakeAddressTRC20usdtNotReal99x");
        WithdrawalRequest expected = withdrawalRequestService.create(
                client.getId(), 1000, "TX9zFakeAddressTRC20usdtNotReal99x"
        );
        Page<ClientWithdrawalRequestResponse> actual = withdrawalRequestService.findAll(
                WithdrawalRequestSpecifications.matches(client.getId(), new ClientWithdrawalRequestFilter(
                        expected.getId(), null, null, null, null
                )),
                Pageable.ofSize(20),
                ClientWithdrawalRequestResponse.class
        );
        assertEquals(1, actual.getTotalElements());
        assertEquals(expected.getId(), actual.getContent().getFirst().id());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "NEW", "APPROVED"
    })
    @DisplayName("Метод должен вернуть заявку по статусу.")
    void findAll_shouldReturnRequestByStatus(WithdrawalRequestStatus status) {
        Client client = getDummyClient(BigDecimal.ONE);
        when(ratesConsumer.consume()).thenReturn(new BigDecimal(80));
        WithdrawalRequest expected = new WithdrawalRequest();
        expected.setStatus(status);
        expected.setClient(client);
        fillFields(expected);
        withdrawalRequestRepository.save(expected);
        Page<ClientWithdrawalRequestResponse> actual = withdrawalRequestService.findAll(
                WithdrawalRequestSpecifications.matches(client.getId(), new ClientWithdrawalRequestFilter(
                        null, status, null, null, null
                )),
                Pageable.ofSize(20),
                ClientWithdrawalRequestResponse.class
        );
        assertEquals(1, actual.getTotalElements());
        assertEquals(expected.getId(), actual.getContent().getFirst().id());
    }

    @ParameterizedTest
    @ValueSource(longs = {
            1790083135024L, 1790073335024L
    })
    @DisplayName("Метод должен вернуть заявку по дате создания ОТ.")
    void findAll_shouldReturnRequestByCreatedFrom(long millis) {
        Client client = getDummyClient(BigDecimal.ONE);
        when(ratesConsumer.consume()).thenReturn(new BigDecimal(80));
        WithdrawalRequest expected = new WithdrawalRequest();
        expected.setCreatedAt(Instant.ofEpochMilli(millis + 50000L));
        expected.setClient(client);
        fillFields(expected);
        withdrawalRequestRepository.save(expected);
        Page<ClientWithdrawalRequestResponse> actual = withdrawalRequestService.findAll(
                WithdrawalRequestSpecifications.matches(client.getId(), new ClientWithdrawalRequestFilter(
                        null, null, Instant.ofEpochMilli(millis), null, null
                )),
                Pageable.ofSize(20),
                ClientWithdrawalRequestResponse.class
        );
        assertEquals(1, actual.getTotalElements());
        assertEquals(expected.getId(), actual.getContent().getFirst().id());
    }

    @ParameterizedTest
    @ValueSource(longs = {
            1790083135024L, 1790073335024L
    })
    @DisplayName("Метод должен вернуть заявку по дате создания ДО.")
    void findAll_shouldReturnRequestByCreatedTo(long millis) {
        Client client = getDummyClient(BigDecimal.ONE);
        when(ratesConsumer.consume()).thenReturn(new BigDecimal(80));
        WithdrawalRequest expected = new WithdrawalRequest();
        expected.setCreatedAt(Instant.ofEpochMilli(millis - 120000L));
        expected.setClient(client);
        fillFields(expected);
        withdrawalRequestRepository.save(expected);
        Page<ClientWithdrawalRequestResponse> actual = withdrawalRequestService.findAll(
                WithdrawalRequestSpecifications.matches(client.getId(), new ClientWithdrawalRequestFilter(
                        null, null, null, Instant.ofEpochMilli(millis), null
                )),
                Pageable.ofSize(20),
                ClientWithdrawalRequestResponse.class
        );
        assertEquals(1, actual.getTotalElements());
        assertEquals(expected.getId(), actual.getContent().getFirst().id());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "TX9zFakeAddressTRC20usdtNotReal99x", "TF7y6fakeTronAddressUSDT88888xxxx1"
    })
    @DisplayName("Метод должен вернуть заявку по адресу кошелька.")
    void findAll_shouldReturnRequestByAddress(String address) {
        Client client = getDummyClient(BigDecimal.ONE);
        when(ratesConsumer.consume()).thenReturn(new BigDecimal(80));
        WithdrawalRequest expected = new WithdrawalRequest();
        expected.setAddress(address);
        expected.setClient(client);
        fillFields(expected);
        withdrawalRequestRepository.save(expected);
        Page<ClientWithdrawalRequestResponse> actual = withdrawalRequestService.findAll(
                WithdrawalRequestSpecifications.matches(client.getId(), new ClientWithdrawalRequestFilter(
                        null, null, null, null, address
                )),
                Pageable.ofSize(20),
                ClientWithdrawalRequestResponse.class
        );
        assertEquals(1, actual.getTotalElements());
        assertEquals(expected.getId(), actual.getContent().getFirst().id());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0e845a87-ebef-44c0-b702-ae996b26f537", "d1171cd9-6100-40cb-afc9-53a530c9a843"
    })
    @DisplayName("Метод должен вернуть бросить BadRequestException если заявка не найдена.")
    void cancel_shouldThrowBadRequestExceptionIfOrderNotFound(UUID id) {
        UUID clientId = UUID.randomUUID();
        assertThrows(BadRequestException.class, () -> withdrawalRequestService.cancel(clientId, id));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "CANCELED", "APPROVED"
    })
    void cancel_shouldThrowBadRequestExceptionIfOrderStatusIsNotNew(WithdrawalRequestStatus status) {
        WithdrawalRequest withdrawalRequest = new WithdrawalRequest();
        withdrawalRequest.setStatus(status);
        fillFields(withdrawalRequest);
        withdrawalRequestRepository.save(withdrawalRequest);

        UUID clientId = withdrawalRequest.getClient().getId();
        UUID requestId = withdrawalRequest.getId();
        assertThrows(BadRequestException.class, () -> withdrawalRequestService.cancel(clientId, requestId));
    }

    @RepeatedTest(value = 2)
    @DisplayName("Метод должен вернуть бросить BadRequestException если заявка не клиента, делающего запрос.")
    void cancel_shouldThrowBadRequestExceptionIfOrderNotClientOrder() {
        Client client = getDummyClient(BigDecimal.ONE);
        Client targetClient = getDummyClient(BigDecimal.ONE);
        WithdrawalRequest request = new WithdrawalRequest();
        request.setClient(client);
        fillFields(request);
        withdrawalRequestRepository.save(request);

        UUID clientId = targetClient.getId();
        UUID requestId = request.getId();
        assertThrows(BadRequestException.class, () -> withdrawalRequestService.cancel(clientId, requestId));
    }

    @RepeatedTest(value = 2)
    @DisplayName("Метод должен обновить статус заявки до CANCEL.")
    void cancel_shouldUpdateStatus() {
        Client client = getDummyClient(BigDecimal.ONE);
        WithdrawalRequest request = new WithdrawalRequest();
        request.setClient(client);
        fillFields(request);
        withdrawalRequestRepository.save(request);
        withdrawalRequestService.cancel(client.getId(), request.getId());
        Optional<WithdrawalRequest> maybeRequest = withdrawalRequestRepository.findById(request.getId());
        assertTrue(maybeRequest.isPresent());
        assertEquals(WithdrawalRequestStatus.CANCELED, maybeRequest.get().getStatus());
    }
}