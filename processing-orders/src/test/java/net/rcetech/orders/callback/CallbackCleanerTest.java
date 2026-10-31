package net.rcetech.orders.callback;

import net.rcetech.domain.config.IncidentConfig;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.ClientCallback;
import net.rcetech.domain.model.orders.MerchantCallback;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.repository.orders.ClientCallbackRepository;
import net.rcetech.domain.repository.orders.MerchantCallbackRepository;
import net.rcetech.domain.repository.orders.OrderRepository;
import net.rcetech.domain.service.incidents.IncidentService;
import net.rcetech.domain.service.orders.ClientCallbackService;
import net.rcetech.domain.service.orders.MerchantCallbackService;
import net.rcetech.domain.service.support.SupportUserService;
import net.rcetech.domain.test.SharedMySQLContainer;
import net.rcetech.meta.orders.ClientCallbackStatus;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.RequestMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@Import({CallbackCleaner.class, ClientCallbackService.class, MerchantCallbackService.class, IncidentService.class, SupportUserService.class,
        IncidentConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CallbackCleanerTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        SharedMySQLContainer.register(registry);
    }

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ClientCallbackRepository clientCallbackRepository;

    @Autowired
    private ClientCallbackService clientCallbackService;

    @Autowired
    private MerchantCallbackRepository merchantCallbackRepository;

    @Autowired
    private CallbackCleaner callbackCleaner;

    @Autowired
    private TestEntityManager entityManager;

    @ParameterizedTest
    @CsvSource({
            "SENT,15,false",
            "FAILED,30,false",
            "SENT,13,true",
            "FAILED,1,true",
            "PENDING,15,true",
            "SENDING,30,true"
    })
    @DisplayName("Должны удаляться только отправленные и недоставленные КБ старше 14 дней.")
    void deleteOld_shouldDeleteOnlyFinishedOlderThanFourteenDays(ClientCallbackStatus status, long daysAgo,
                                                              boolean kept) {
        ClientCallback callback = clientCallbackService.create(saveOrder().getId(), OrderStatus.SUCCESS,
                "https://example.com/callback", "{}");
        entityManager.getEntityManager()
                .createQuery("update ClientCallback c set c.status = :status, c.createdAt = :createdAt where c.id = :id")
                .setParameter("status", status)
                .setParameter("createdAt", Instant.now().minus(Duration.ofDays(daysAgo)))
                .setParameter("id", callback.getId())
                .executeUpdate();

        callbackCleaner.deleteOld();

        assertEquals(kept, clientCallbackRepository.existsById(callback.getId()));
    }

    @ParameterizedTest
    @CsvSource({
            "15,false,false",
            "30,true,false",
            "15,true,true",
            "13,false,false",
            "1,true,true",
            "0,false,true"
    })
    @DisplayName("Должны удаляться все КБ мерчантов старше 14 дней, обработанные и необработанные.")
    void deleteOld_shouldDeleteMerchantCallbacksOlderThanFourteenDays(long daysAgo, boolean processed,
                                                                   boolean withOrder) {
        Instant createdAt = Instant.now().minus(Duration.ofDays(daysAgo)).minusSeconds(60);
        MerchantCallback callback = merchantCallbackRepository.save(MerchantCallback.builder()
                .createdAt(createdAt)
                .processedAt(processed ? createdAt.plusSeconds(5) : null)
                .order(withOrder ? saveOrder() : null)
                .merchant(Merchant.ALFA_TEAM)
                .merchantOrderId(UUID.randomUUID().toString())
                .status("SUCCESS")
                .build());

        callbackCleaner.deleteOld();
        entityManager.clear();

        assertEquals(daysAgo < 14, merchantCallbackRepository.existsById(callback.getId()));
    }

    private Order saveOrder() {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setUsername("test" + client.getId());
        client.setRegisteredAt(Instant.now());
        clientRepository.save(client);
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCreatedAt(Instant.now());
        order.setExpiresAt(Instant.now().plusSeconds(900));
        order.setClient(client);
        order.setInternalId(UUID.randomUUID().toString());
        order.setStatus(OrderStatus.SUCCESS);
        order.setAmount(5000);
        order.setMerchant(Merchant.ALFA_TEAM);
        order.setMerchantOrderId(UUID.randomUUID().toString());
        order.setMethod(RequestMethod.CARD);
        order.setDetails("1234 1234 1234 1234");
        order.setBank("ALFA");
        return orderRepository.save(order);
    }
}
