package net.rcetech.domain.service.clients;

import net.rcetech.domain.model.billing.Transaction;
import net.rcetech.domain.model.billing.WithdrawalRequest;
import net.rcetech.domain.model.clients.ApiKey;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.MerchantCallback;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.test.SharedMySQLContainer;
import net.rcetech.meta.billing.Operation;
import net.rcetech.meta.billing.TransactionType;
import net.rcetech.meta.billing.WithdrawalRequestStatus;
import net.rcetech.meta.clients.ClientDeletedEvent;
import net.rcetech.meta.exception.BadRequestException;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.RequestMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@RecordApplicationEvents
@Import(ClientDeletionService.class)
class ClientDeletionServiceTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        SharedMySQLContainer.register(registry);
    }

    @Autowired
    private ClientDeletionService clientDeletionService;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ApplicationEvents applicationEvents;

    @CsvSource({
            "1,1,1,1,1",
            "3,2,4,5,2",
            "0,0,0,0,0"
    })
    @ParameterizedTest
    @DisplayName("Метод должен удалить клиента, его ордера, заявки на вывод, транзакции, апи-ключи и методы.")
    void delete_shouldDeleteClientData(int orders, int callbacksPerOrder, int withdrawalRequests, int transactions,
                                       int apiKeys) {
        Client client = createClientWithData(orders, callbacksPerOrder, withdrawalRequests, transactions, apiKeys);
        UUID id = client.getId();

        clientDeletionService.delete(id);
        entityManager.clear();

        assertAll(
                () -> assertNull(entityManager.find(Client.class, id)),
                () -> assertEquals(0, count("select count(o) from Order o where o.client.id = :id", id)),
                () -> assertEquals(0, count("select count(w) from WithdrawalRequest w where w.client.id = :id", id)),
                () -> assertEquals(0, count("select count(t) from Transaction t where t.client.id = :id", id)),
                () -> assertEquals(0, count("select count(k) from ApiKey k where k.client.id = :id", id)),
                () -> assertEquals(0, ((Number) entityManager.getEntityManager()
                        .createNativeQuery("select count(*) from client_method where client_id = ?1")
                        .setParameter(1, toBytes(id))
                        .getSingleResult()).intValue())
        );
    }

    @CsvSource({
            "1,1",
            "2,3"
    })
    @ParameterizedTest
    @DisplayName("Метод должен сохранить КБ ордеров клиента, затерев в них ордер.")
    void delete_shouldDetachCallbacks(int orders, int callbacksPerOrder) {
        Client client = createClientWithData(orders, callbacksPerOrder, 0, 0, 0);
        List<Long> callbackIds = entityManager.getEntityManager()
                .createQuery("select c.id from MerchantCallback c where c.order.client.id = :id", Long.class)
                .setParameter("id", client.getId())
                .getResultList();

        clientDeletionService.delete(client.getId());
        entityManager.clear();

        assertEquals(orders * callbacksPerOrder, callbackIds.size());
        for (Long callbackId : callbackIds) {
            MerchantCallback callback = entityManager.find(MerchantCallback.class, callbackId);
            assertNotNull(callback);
            assertNull(callback.getOrder());
        }
    }

    @CsvSource({
            "1,1,1,1,1",
            "2,3,2,4,3"
    })
    @ParameterizedTest
    @DisplayName("Метод не должен затрагивать данные других клиентов.")
    void delete_shouldNotTouchOtherClients(int orders, int callbacksPerOrder, int withdrawalRequests,
                                           int transactions, int apiKeys) {
        Client client = createClientWithData(orders, callbacksPerOrder, withdrawalRequests, transactions, apiKeys);
        Client other = createClientWithData(orders, callbacksPerOrder, withdrawalRequests, transactions, apiKeys);
        UUID otherId = other.getId();

        clientDeletionService.delete(client.getId());
        entityManager.clear();

        assertAll(
                () -> assertNotNull(entityManager.find(Client.class, otherId)),
                () -> assertEquals(orders, count("select count(o) from Order o where o.client.id = :id", otherId)),
                () -> assertEquals(orders * callbacksPerOrder,
                        count("select count(c) from MerchantCallback c where c.order.client.id = :id", otherId)),
                () -> assertEquals(withdrawalRequests,
                        count("select count(w) from WithdrawalRequest w where w.client.id = :id", otherId)),
                () -> assertEquals(transactions,
                        count("select count(t) from Transaction t where t.client.id = :id", otherId)),
                () -> assertEquals(apiKeys, count("select count(k) from ApiKey k where k.client.id = :id", otherId))
        );
    }

    @ValueSource(ints = {0, 3})
    @ParameterizedTest
    @DisplayName("Метод должен опубликовать событие удаления клиента.")
    void delete_shouldPublishEvent(int orders) {
        Client client = createClientWithData(orders, 1, 1, 1, 1);

        clientDeletionService.delete(client.getId());

        assertEquals(List.of(new ClientDeletedEvent(client.getId())),
                applicationEvents.stream(ClientDeletedEvent.class).toList());
    }

    @ValueSource(strings = {
            "185975b3-094a-43fb-a1a5-7128c39b39ab",
            "c44cc597-7142-4636-b5f6-799d352fd631"
    })
    @ParameterizedTest
    @DisplayName("Метод должен выбросить исключение и не публиковать событие, если клиент не найден.")
    void delete_shouldThrowBadRequestIfClientNotFound(UUID id) {
        createClientWithData(1, 1, 1, 1, 1);

        assertThrows(BadRequestException.class, () -> clientDeletionService.delete(id));
        assertEquals(0, applicationEvents.stream(ClientDeletedEvent.class).count());
    }

    private static byte[] toBytes(UUID id) {
        return ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
    }

    private long count(String query, UUID clientId) {
        return entityManager.getEntityManager()
                .createQuery(query, Long.class)
                .setParameter("id", clientId)
                .getSingleResult();
    }

    private Client createClientWithData(int orders, int callbacksPerOrder, int withdrawalRequests, int transactions,
                                        int apiKeys) {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setUsername("test_" + client.getId());
        client.setRegisteredAt(Instant.now());
        client.setBalance(0);
        client.setMethods(new TreeSet<>(List.of(RequestMethod.CARD, RequestMethod.SBP)));
        client.setApiKeys(new ArrayList<>());
        for (int i = 0; i < apiKeys; i++) {
            client.getApiKeys().add(new ApiKey(null, "preview" + i, "key" + i, UUID.randomUUID().toString(), client));
        }
        entityManager.persist(client);
        for (int i = 0; i < orders; i++) {
            Order order = entityManager.persist(createOrder(client));
            for (int j = 0; j < callbacksPerOrder; j++) {
                entityManager.persist(MerchantCallback.builder()
                        .createdAt(Instant.now())
                        .order(order)
                        .merchant(order.getMerchant())
                        .merchantOrderId(order.getMerchantOrderId())
                        .status("SUCCESS")
                        .build());
            }
        }
        for (int i = 0; i < withdrawalRequests; i++) {
            entityManager.persist(WithdrawalRequest.builder()
                    .id(UUID.randomUUID())
                    .client(client)
                    .status(WithdrawalRequestStatus.NEW)
                    .createdAt(Instant.now())
                    .grossSourceAmount(10000)
                    .commissionPercent(new BigDecimal("2.5"))
                    .netSourceAmount(9750)
                    .rate(new BigDecimal("95.5"))
                    .targetAmount(102)
                    .address("TXyz" + i)
                    .build());
        }
        for (int i = 0; i < transactions; i++) {
            entityManager.persist(Transaction.builder()
                    .client(client)
                    .createdAt(Instant.now())
                    .operation(i % 2 == 0 ? Operation.CREDIT : Operation.DEBIT)
                    .amount(1000 + i)
                    .type(TransactionType.MANUAL_CORRECT)
                    .build());
        }
        entityManager.flush();
        entityManager.clear();
        return client;
    }

    private Order createOrder(Client client) {
        Instant now = Instant.now();
        return Order.builder()
                .id(UUID.randomUUID())
                .createdAt(now)
                .expiresAt(now.plusSeconds(900))
                .client(client)
                .internalId(UUID.randomUUID().toString())
                .status(OrderStatus.SUCCESS)
                .amount(5000)
                .merchant(Merchant.ALFA_TEAM)
                .merchantOrderId(UUID.randomUUID().toString())
                .method(RequestMethod.CARD)
                .build();
    }
}
