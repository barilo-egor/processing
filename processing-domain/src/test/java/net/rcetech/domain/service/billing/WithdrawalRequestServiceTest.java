package net.rcetech.domain.service.billing;

import net.rcetech.domain.mapping.clients.ClientMapper;
import net.rcetech.domain.model.billing.WithdrawalRequest;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.repository.billing.WithdrawalRequestRepository;
import net.rcetech.domain.repository.billing.WithdrawalRequestSpecifications;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.domain.test.SharedMySQLContainer;
import net.rcetech.meta.billing.WithdrawalRequestStatus;
import net.rcetech.meta.billing.dto.ClientWithdrawalRequestResponse;
import net.rcetech.meta.billing.dto.WithdrawalRequestFilter;
import net.rcetech.meta.exception.BadRequestException;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.incidents.ClientCommissionPercentNotSetEvent;
import net.rcetech.meta.incidents.ClientMinWithdrawalAmountNotSetEvent;
import net.rcetech.rates.RatePair;
import net.rcetech.rates.RateService;
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
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ClientService.class, WithdrawalRequestService.class})
@RecordApplicationEvents
class WithdrawalRequestServiceTest {

    @TestConfiguration
    static class Configuration {

        @Bean
        public ClientMapper clientMapper() {
            return Mappers.getMapper(ClientMapper.class);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        SharedMySQLContainer.register(registry);
    }

    @MockitoBean
    private RateService rateService;

    @Autowired
    private ApplicationEvents applicationEvents;

    @MockitoBean
    private TransactionService transactionService;

    @Autowired
    private WithdrawalRequestService withdrawalRequestService;

    @Autowired
    private WithdrawalRequestRepository withdrawalRequestRepository;

    @Autowired
    private ClientRepository clientRepository;

    @BeforeEach
    void setUp() {
        when(transactionService.calculateBalance(any())).thenReturn(Integer.MAX_VALUE);
    }

    Client getDummyClient(BigDecimal commissionPercent) {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setUsername("test" + client.getId());
        client.setRegisteredAt(Instant.now());
        client.setCommissionPercent(commissionPercent);
        client.setMinWithdrawalAmount(1000);
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

    void assertErrorTimestamp(BaseException exception, long before, long after) {
        assertNotNull(exception.getTimestamp());
        assertTrue(exception.getTimestamp() >= before && exception.getTimestamp() <= after);
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

    @ParameterizedTest
    @ValueSource(strings = {"15", "21.5"})
    @DisplayName("Метод должен создать инцидент и бросить BaseException, если клиенту не установлена минимальная сумма вывода.")
    void create_shouldCreateIncidentIfMinWithdrawalAmountNull(BigDecimal commissionPercent) {
        Client client = getDummyClient(commissionPercent);
        client.setMinWithdrawalAmount(null);
        clientRepository.save(client);
        UUID clientId = client.getId();
        long before = System.currentTimeMillis();

        BaseException exception = assertThrows(
                BaseException.class,
                () -> withdrawalRequestService.create(clientId, 5000, "TX9zFakeAddressTRC20usdtNotReal99x")
        );

        long after = System.currentTimeMillis();
        assertErrorTimestamp(exception, before, after);
        assertEquals(List.of(new ClientMinWithdrawalAmountNotSetEvent(clientId, exception.getTimestamp())),
                applicationEvents.stream(ClientMinWithdrawalAmountNotSetEvent.class).toList());
        assertEquals(0, withdrawalRequestRepository.count());
    }

    @ParameterizedTest
    @CsvSource({
            "5000,4999",
            "1,0",
            "25000,18500"
    })
    @DisplayName("Метод должен бросить BadRequestException, если минимальная сумма вывода больше запрошенной суммы.")
    void create_shouldThrowBadRequestExceptionIfAmountLessThanClientMinWithdrawalAmount(Integer minWithdrawalAmount,
                                                                                        Integer amount) {
        Client client = getDummyClient(new BigDecimal(15));
        client.setMinWithdrawalAmount(minWithdrawalAmount);
        clientRepository.save(client);
        UUID clientId = client.getId();
        assertThrows(
                BadRequestException.class,
                () -> withdrawalRequestService.create(clientId, amount, null),
                "Запрошенная сумма на вывод меньше минимально допустимой."
        );
    }

    @ParameterizedTest
    @CsvSource({
            "4999,5000",
            "0,1000",
            "-300,2500",
            "18500,25000"
    })
    @DisplayName("Метод должен бросить BadRequestException, если запрошенная сумма больше баланса клиента.")
    void create_shouldThrowBadRequestExceptionIfAmountGreaterThanBalance(Integer balance, Integer amount) {
        Client client = getDummyClient(new BigDecimal(15));
        UUID clientId = client.getId();
        when(transactionService.calculateBalance(clientId)).thenReturn(balance);

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> withdrawalRequestService.create(clientId, amount, "TX9zFakeAddressTRC20usdtNotReal99x")
        );

        assertEquals("На балансе недостаточно средств.", exception.getMessage());
        assertEquals(0, withdrawalRequestRepository.count());
    }

    @ParameterizedTest
    @ValueSource(ints = {1000, 74320})
    @DisplayName("Метод должен создать заявку, если запрошенная сумма равна балансу клиента.")
    void create_shouldCreateRequestIfAmountEqualsBalance(Integer amount) {
        Client client = getDummyClient(new BigDecimal(15));
        when(transactionService.calculateBalance(client.getId())).thenReturn(amount);
        when(rateService.getRate(RatePair.USDT_RUB)).thenReturn(new BigDecimal(80));

        WithdrawalRequest actual = withdrawalRequestService.create(client.getId(), amount, "TX9zFakeAddressTRC20usdtNotReal99x");

        assertEquals(amount, actual.getGrossSourceAmount());
        assertEquals(1, withdrawalRequestRepository.count());
    }

    @ParameterizedTest
    @ValueSource(ints = {1000, 25000})
    @DisplayName("Метод должен создать инцидент и бросить BaseException, если клиенту не установлен процент комиссии.")
    void create_shouldCreateIncidentIfClientCommissionPercentIsNull(Integer minWithdrawalAmount) {
        Client client = getDummyClient(null);
        client.setMinWithdrawalAmount(minWithdrawalAmount);
        clientRepository.save(client);
        UUID clientId = client.getId();
        long before = System.currentTimeMillis();

        BaseException exception = assertThrows(
                BaseException.class,
                () -> withdrawalRequestService.create(clientId, 30000, "TX9zFakeAddressTRC20usdtNotReal99x")
        );

        long after = System.currentTimeMillis();
        assertErrorTimestamp(exception, before, after);
        assertEquals(List.of(new ClientCommissionPercentNotSetEvent(clientId, exception.getTimestamp())),
                applicationEvents.stream(ClientCommissionPercentNotSetEvent.class).toList());
        assertEquals(0, withdrawalRequestRepository.count());
    }

    @ParameterizedTest
    @CsvSource({
            "5357,TX9zFakeAddressTRC20usdtNotReal99x,70,15.5,4527,65",
            "85330,TF7y6fakeTronAddressUSDT88888xxxx1,80,21,67411,843",
            "2450,TF7y6fakeTronAddressUSDT88888xxxx1,74.5,14.4,2097,28"
    })
    @DisplayName("Метод должен создать заявку.")
    void create_shouldCreateRequest(Integer amount, String address, BigDecimal rate, BigDecimal commissionPercent,
                                    Integer expectedNetAmount, Integer expectedTargetAmount) {
        Client client = getDummyClient(commissionPercent);
        when(rateService.getRate(RatePair.USDT_RUB)).thenReturn(rate);

        withdrawalRequestService.create(client.getId(), amount, address);
        List<WithdrawalRequest> requests = withdrawalRequestRepository.findAll();

        assertEquals(1, requests.size());
        WithdrawalRequest actual = requests.getFirst();
        assertAll(
                () -> assertNotNull(actual.getId()),
                () -> assertEquals(client.getId(), actual.getClient().getId()),
                () -> assertEquals(WithdrawalRequestStatus.NEW, actual.getStatus()),
                () -> assertNotNull(actual.getCreatedAt()),
                () -> assertEquals(amount, actual.getGrossSourceAmount()),
                () -> assertEquals(client.getCommissionPercent(), commissionPercent),
                () -> assertEquals(expectedNetAmount, actual.getNetSourceAmount()),
                () -> assertEquals(rate, actual.getRate()),
                () -> assertEquals(expectedTargetAmount, actual.getTargetAmount()),
                () -> assertEquals(address, actual.getAddress())
        );
    }

    @RepeatedTest(value = 2)
    @DisplayName("Метод должен вернуть заявки клиента.")
    void findAll_shouldReturnClientRequests() {
        when(rateService.getRate(RatePair.USDT_RUB)).thenReturn(new BigDecimal(80));
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
        when(rateService.getRate(RatePair.USDT_RUB)).thenReturn(new BigDecimal(80));
        withdrawalRequestService.create(client.getId(), 5000, "TX9zFakeAddressTRC20usdtNotReal99x");
        WithdrawalRequest expected = withdrawalRequestService.create(
                client.getId(), 1000, "TX9zFakeAddressTRC20usdtNotReal99x"
        );

        Page<ClientWithdrawalRequestResponse> actual = withdrawalRequestService.findAll(
                WithdrawalRequestSpecifications.matches(client.getId(), new WithdrawalRequestFilter(
                        expected.getId(), null, null, null, null, null
                )),
                Pageable.ofSize(20),
                ClientWithdrawalRequestResponse.class
        );

        assertEquals(1, actual.getTotalElements());
        assertEquals(expected.getId(), actual.getContent().getFirst().id());
    }

    @Test
    @DisplayName("Метод должен вернуть заявку по id клиента.")
    void findAllShouldReturnRequestByClientId() {
        Client client = getDummyClient(BigDecimal.ONE);
        Client targetClient = getDummyClient(BigDecimal.ONE);
        assertNotNull(targetClient.getId());
        when(rateService.getRate(RatePair.USDT_RUB)).thenReturn(new BigDecimal(80));
        withdrawalRequestService.create(client.getId(), 5000, "TX9zFakeAddressTRC20usdtNotReal99x");
        WithdrawalRequest expected = withdrawalRequestService.create(
                targetClient.getId(), 1000, "TX9zFakeAddressTRC20usdtNotReal99x"
        );

        Page<ClientWithdrawalRequestResponse> actual = withdrawalRequestService.findAll(
                WithdrawalRequestSpecifications.matches(null, new WithdrawalRequestFilter(
                        null, targetClient.getId().toString(), null, null, null, null
                )),
                Pageable.ofSize(20),
                ClientWithdrawalRequestResponse.class
        );

        assertEquals(1, actual.getTotalElements());
        assertEquals(expected.getId(), actual.getContent().getFirst().id());
    }

    @Test
    @DisplayName("Метод должен вернуть заявку по username клиента.")
    void findAllShouldReturnRequestByClientUsername() {
        Client client = getDummyClient(BigDecimal.ONE);
        Client targetClient = getDummyClient(BigDecimal.ONE);
        assertNotNull(targetClient.getId());
        when(rateService.getRate(RatePair.USDT_RUB)).thenReturn(new BigDecimal(80));
        withdrawalRequestService.create(client.getId(), 5000, "TX9zFakeAddressTRC20usdtNotReal99x");
        WithdrawalRequest expected = withdrawalRequestService.create(
                targetClient.getId(), 1000, "TX9zFakeAddressTRC20usdtNotReal99x"
        );

        Page<ClientWithdrawalRequestResponse> actual = withdrawalRequestService.findAll(
                WithdrawalRequestSpecifications.matches(null, new WithdrawalRequestFilter(
                        null, targetClient.getUsername(), null, null, null, null
                )),
                Pageable.ofSize(20),
                ClientWithdrawalRequestResponse.class
        );

        assertEquals(1, actual.getTotalElements());
        assertEquals(expected.getId(), actual.getContent().getFirst().id());
    }

    @Test
    @DisplayName("Метод не должен искать клиента по username, если передан идентификатор клиента.")
    void findAll_shouldNotFindByClientUsernameIfClientIdPassed() {
        Client client = getDummyClient(BigDecimal.ONE);
        Client targetClient = getDummyClient(BigDecimal.ONE);
        when(rateService.getRate(RatePair.USDT_RUB)).thenReturn(new BigDecimal(80));

        withdrawalRequestService.create(client.getId(), 5000, "TX9zFakeAddressTRC20usdtNotReal99x");
        WithdrawalRequest expected = withdrawalRequestService.create(
                targetClient.getId(), 5000, "TX9zFakeAddressTRC20usdtNotReal99x"
        );

        Page<ClientWithdrawalRequestResponse> actual = withdrawalRequestService.findAll(
                WithdrawalRequestSpecifications.matches(targetClient.getId(), new WithdrawalRequestFilter(
                        null, client.getUsername(), null, null, null, null
                )),
                Pageable.ofSize(20),
                ClientWithdrawalRequestResponse.class
        );

        assertEquals(1, actual.getTotalElements());
        assertEquals(expected.getId(), actual.getContent().getFirst().id());
    }

    @Test
    @DisplayName("Метод не должен искать клиента по id, если передан идентификатор клиента.")
    void findAll_shouldNotFindByClientIdInParameterIfClientIdPassed() {
        Client client = getDummyClient(BigDecimal.ONE);
        assertNotNull(client.getId());
        Client targetClient = getDummyClient(BigDecimal.ONE);
        when(rateService.getRate(RatePair.USDT_RUB)).thenReturn(new BigDecimal(80));

        withdrawalRequestService.create(client.getId(), 5000, "TX9zFakeAddressTRC20usdtNotReal99x");
        WithdrawalRequest expected = withdrawalRequestService.create(
                targetClient.getId(), 5000, "TX9zFakeAddressTRC20usdtNotReal99x"
        );

        Page<ClientWithdrawalRequestResponse> actual = withdrawalRequestService.findAll(
                WithdrawalRequestSpecifications.matches(targetClient.getId(), new WithdrawalRequestFilter(
                        null, client.getId().toString(), null, null, null, null
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
        when(rateService.getRate(RatePair.USDT_RUB)).thenReturn(new BigDecimal(80));
        WithdrawalRequest expected = new WithdrawalRequest();
        expected.setStatus(status);
        expected.setClient(client);
        fillFields(expected);
        withdrawalRequestRepository.save(expected);
        Page<ClientWithdrawalRequestResponse> actual = withdrawalRequestService.findAll(
                WithdrawalRequestSpecifications.matches(client.getId(), new WithdrawalRequestFilter(
                        null, null, status, null, null, null
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
        when(rateService.getRate(RatePair.USDT_RUB)).thenReturn(new BigDecimal(80));
        WithdrawalRequest expected = new WithdrawalRequest();
        expected.setCreatedAt(Instant.ofEpochMilli(millis + 50000L));
        expected.setClient(client);
        fillFields(expected);
        withdrawalRequestRepository.save(expected);
        Page<ClientWithdrawalRequestResponse> actual = withdrawalRequestService.findAll(
                WithdrawalRequestSpecifications.matches(client.getId(), new WithdrawalRequestFilter(
                        null,null,  null, Instant.ofEpochMilli(millis), null, null
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
        when(rateService.getRate(RatePair.USDT_RUB)).thenReturn(new BigDecimal(80));
        WithdrawalRequest expected = new WithdrawalRequest();
        expected.setCreatedAt(Instant.ofEpochMilli(millis - 120000L));
        expected.setClient(client);
        fillFields(expected);
        withdrawalRequestRepository.save(expected);
        Page<ClientWithdrawalRequestResponse> actual = withdrawalRequestService.findAll(
                WithdrawalRequestSpecifications.matches(client.getId(), new WithdrawalRequestFilter(
                        null, null, null, null, Instant.ofEpochMilli(millis), null
                )),
                Pageable.ofSize(20),
                ClientWithdrawalRequestResponse.class
        );
        assertEquals(1, actual.getTotalElements());
        assertEquals(expected.getId(), actual.getContent().getFirst().id());
    }

    @ParameterizedTest
    @ValueSource(longs = {
            1790083135000L, 1790073336000L
    })
    @DisplayName("Метод должен включать границу даты создания ОТ.")
    void findAll_shouldIncludeCreatedFromBoundary(long millis) {
        Instant from = Instant.ofEpochMilli(millis);
        Client client = getDummyClient(BigDecimal.ONE);
        when(rateService.getRate(RatePair.USDT_RUB)).thenReturn(new BigDecimal(80));
        WithdrawalRequest expected = saveRequest(client, from);
        saveRequest(client, from.minusSeconds(1));
        Page<ClientWithdrawalRequestResponse> actual = withdrawalRequestService.findAll(
                WithdrawalRequestSpecifications.matches(client.getId(), new WithdrawalRequestFilter(
                        null, null, null, from, null, null
                )),
                Pageable.ofSize(20),
                ClientWithdrawalRequestResponse.class
        );
        assertEquals(1, actual.getTotalElements());
        assertEquals(expected.getId(), actual.getContent().getFirst().id());
    }

    @ParameterizedTest
    @ValueSource(longs = {
            1790083135000L, 1790073336000L
    })
    @DisplayName("Метод должен исключать границу даты создания ДО.")
    void findAll_shouldExcludeCreatedToBoundary(long millis) {
        Instant to = Instant.ofEpochMilli(millis);
        Client client = getDummyClient(BigDecimal.ONE);
        when(rateService.getRate(RatePair.USDT_RUB)).thenReturn(new BigDecimal(80));
        saveRequest(client, to);
        WithdrawalRequest expected = saveRequest(client, to.minusSeconds(1));
        Page<ClientWithdrawalRequestResponse> actual = withdrawalRequestService.findAll(
                WithdrawalRequestSpecifications.matches(client.getId(), new WithdrawalRequestFilter(
                        null, null, null, null, to, null
                )),
                Pageable.ofSize(20),
                ClientWithdrawalRequestResponse.class
        );
        assertEquals(1, actual.getTotalElements());
        assertEquals(expected.getId(), actual.getContent().getFirst().id());
    }

    WithdrawalRequest saveRequest(Client client, Instant createdAt) {
        WithdrawalRequest withdrawalRequest = new WithdrawalRequest();
        withdrawalRequest.setCreatedAt(createdAt);
        withdrawalRequest.setClient(client);
        fillFields(withdrawalRequest);
        return withdrawalRequestRepository.save(withdrawalRequest);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "TX9zFakeAddressTRC20usdtNotReal99x", "TF7y6fakeTronAddressUSDT88888xxxx1"
    })
    @DisplayName("Метод должен вернуть заявку по адресу кошелька.")
    void findAll_shouldReturnRequestByAddress(String address) {
        Client client = getDummyClient(BigDecimal.ONE);
        when(rateService.getRate(RatePair.USDT_RUB)).thenReturn(new BigDecimal(80));
        WithdrawalRequest expected = new WithdrawalRequest();
        expected.setAddress(address);
        expected.setClient(client);
        fillFields(expected);
        withdrawalRequestRepository.save(expected);
        Page<ClientWithdrawalRequestResponse> actual = withdrawalRequestService.findAll(
                WithdrawalRequestSpecifications.matches(client.getId(), new WithdrawalRequestFilter(
                        null, null, null, null, null, address
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
    @DisplayName("Метод должен бросить BadRequestException, если статус ордера не NEW.")
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

    @ParameterizedTest
    @CsvSource({
            "0fae41bc-c171-4ad7-b4bc-1132a8149407,APPROVED",
            "186f758e-53ab-4973-98af-a924d1b94a10,CANCELED"
    })
    @DisplayName("Метод должен бросить BadRequestException, если заявка не найдена")
    void updateStatus_shouldThrowBadRequestExceptionIfOrderNotFound(UUID id, WithdrawalRequestStatus status) {
        assertThrows(BadRequestException.class, () -> withdrawalRequestService.updateStatus(id, status));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "CANCELED", "APPROVED"
    })
    @DisplayName("Метод должен бросить BadRequestException, если статус не NEW.")
    void updateStatus_shouldThrowBadRequestExceptionIfOrderStatusIsNotNew(WithdrawalRequestStatus status) {
        WithdrawalRequest withdrawalRequest = new WithdrawalRequest();
        withdrawalRequest.setStatus(status);
        fillFields(withdrawalRequest);
        withdrawalRequestRepository.save(withdrawalRequest);

        UUID requestId = withdrawalRequest.getId();
        assertThrows(BadRequestException.class, () -> withdrawalRequestService.updateStatus(requestId, status));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "APPROVED",
            "CANCELED"
    })
    @DisplayName("Метод должен успешно обновить статус.")
    void updateStatus_shouldUpdateStatus(WithdrawalRequestStatus status) {
        WithdrawalRequest withdrawalRequest = new WithdrawalRequest();
        withdrawalRequest.setStatus(WithdrawalRequestStatus.NEW);
        fillFields(withdrawalRequest);
        withdrawalRequestRepository.save(withdrawalRequest);

        withdrawalRequestService.updateStatus(withdrawalRequest.getId(), status);

        Optional<WithdrawalRequest> maybeRequest =  withdrawalRequestRepository.findById(withdrawalRequest.getId());
        assertTrue(maybeRequest.isPresent());
        assertEquals(status, maybeRequest.get().getStatus());
    }
}