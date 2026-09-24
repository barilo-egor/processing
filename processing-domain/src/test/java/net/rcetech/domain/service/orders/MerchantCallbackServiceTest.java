package net.rcetech.domain.service.orders;

import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.MerchantCallback;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.repository.clients.ClientRepository;
import net.rcetech.domain.repository.orders.MerchantCallbackRepository;
import net.rcetech.domain.repository.orders.MerchantCallbackSpecifications;
import net.rcetech.domain.repository.orders.OrderRepository;
import net.rcetech.meta.billing.dto.MerchantCallbackFilter;
import net.rcetech.meta.billing.dto.MerchantCallbackResponse;
import net.rcetech.meta.clients.ClientStatus;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.RequestMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MerchantCallbackService.class)
@Testcontainers
class MerchantCallbackServiceTest {

    @Container
    static MySQLContainer mySQLContainer = new MySQLContainer("mysql:8.0.46");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mySQLContainer::getUsername);
        registry.add("spring.datasource.password", mySQLContainer::getPassword);
    }

    @Autowired
    private MerchantCallbackRepository merchantCallbackRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private MerchantCallbackService merchantCallbackService;

    Client getDummyClient() {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setUsername("test" + client.getId());
        client.setRegisteredAt(Instant.now());
        client.setStatus(ClientStatus.ACTIVE);
        return clientRepository.save(client);
    }

    Order getDummyOrder(Client client) {
        Order order = new Order();
        fillFields(order, client);
        return orderRepository.save(order);
    }

    void fillFields(MerchantCallback merchantCallback) {
        if (Objects.isNull(merchantCallback.getCreatedAt())) {
            merchantCallback.setCreatedAt(Instant.now());
        }
        if (Objects.isNull(merchantCallback.getMerchant())) {
            merchantCallback.setMerchant(Merchant.ALFA_TEAM);
        }
        if (Objects.isNull(merchantCallback.getMerchantOrderId())) {
            merchantCallback.setMerchantOrderId(UUID.randomUUID().toString());
        }
        if (Objects.isNull(merchantCallback.getStatus())) {
            merchantCallback.setStatus("PAID");
        }
        if (Objects.isNull(merchantCallback.getStatusDescription())) {
            merchantCallback.setStatusDescription("Оплачено");
        }
    }

    void fillFields(Order order, Client client) {
        if (order.getId() == null) {
            order.setId(UUID.randomUUID());
        }
        if (order.getCreatedAt() == null) {
            order.setCreatedAt(Instant.now());
        }
        if (order.getExpiresAt() == null) {
            order.setExpiresAt(Instant.now().plusSeconds(900));
        }
        if (order.getClient() == null && client != null) {
            order.setClient(client);
        }
        if (order.getInternalId() == null) {
            order.setInternalId(UUID.randomUUID().toString());
        }
        if (order.getStatus() == null) {
            order.setStatus(OrderStatus.NEW);
        }
        if (order.getAmount() == null) {
            order.setAmount(5000);
        }
        if (order.getMerchant() == null) {
            order.setMerchant(Merchant.ALFA_TEAM);
        }
        if (order.getMerchantOrderId() == null) {
            order.setMerchantOrderId(UUID.randomUUID().toString());
        }
        if (order.getMerchantOrderStatus() == null) {
            order.setMerchantOrderStatus("NEW");
        }
        if (order.getMethod() == null) {
            order.setMethod(RequestMethod.CARD);
        }
        if (order.getDetails() == null) {
            order.setDetails("1234 1234 1234 1234");
        }
        if (order.getBank() == null) {
            order.setBank("ALFA");
        }
        if (order.getCallbackUrl() == null) {
            order.setCallbackUrl("https://google.com/callback");
        }
    }

    @Test
    @DisplayName("Метод должен вернуть все КБ, если фильтр равен null.")
    void findAll_shouldReturnAllCallbacksIfNullFilter() {
        Client client = getDummyClient();

        for (int i = 0; i < 3; i++) {
            MerchantCallback merchantCallback = new MerchantCallback();
            merchantCallback.setOrder(getDummyOrder(client));
            fillFields(merchantCallback);
            merchantCallbackRepository.save(merchantCallback);
        }

        Page<MerchantCallbackResponse> callbacks = merchantCallbackService.findAll(
                MerchantCallbackSpecifications.matches(null),
                PageRequest.of(0, 20),
                MerchantCallbackResponse.class
        );

        assertEquals(3, callbacks.getTotalElements());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Метод должен вернуть все КБ, если переданы пустые параметры.")
    void findAll_shouldReturnAllCallbacksIfBlankParameters(String blankParameter) {
        Client client = getDummyClient();

        for (int i = 0; i < 3; i++) {
            MerchantCallback merchantCallback = new MerchantCallback();
            merchantCallback.setOrder(getDummyOrder(client));
            fillFields(merchantCallback);
            merchantCallbackRepository.save(merchantCallback);
        }

        Page<MerchantCallbackResponse> callbacks = merchantCallbackService.findAll(
                MerchantCallbackSpecifications.matches(new MerchantCallbackFilter(
                        null, null, blankParameter, null, null
                )),
                PageRequest.of(0, 20),
                MerchantCallbackResponse.class
        );

        assertEquals(3, callbacks.getTotalElements());
    }

    @RepeatedTest(value = 2)
    void findAll_shouldFindCallbackByOrderId() {
        Client client = getDummyClient();
        MerchantCallback targetCallback = new MerchantCallback();
        Order targetOrder = getDummyOrder(client);
        targetCallback.setOrder(targetOrder);
        fillFields(targetCallback);
        merchantCallbackRepository.save(targetCallback);
        MerchantCallback callback = new MerchantCallback();
        Order order = getDummyOrder(client);
        callback.setOrder(order);
        fillFields(callback);
        merchantCallbackRepository.save(callback);

        Page<MerchantCallbackResponse> callbacks = merchantCallbackService.findAll(
                MerchantCallbackSpecifications.matches(new MerchantCallbackFilter(
                        targetOrder.getId(), null, null, null, null
                )),
                PageRequest.of(0, 20),
                MerchantCallbackResponse.class
        );

        assertEquals(1, callbacks.getTotalElements());
        assertEquals(targetCallback.getId(), callbacks.getContent().getFirst().id());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ALFA_TEAM", "EVO_PAY"
    })
    void findAll_shouldFindCallbackByMerchant(Merchant merchant) {
        Client client = getDummyClient();
        MerchantCallback targetCallback = new MerchantCallback();
        targetCallback.setMerchant(merchant);
        Order targetOrder = getDummyOrder(client);
        targetCallback.setOrder(targetOrder);
        fillFields(targetCallback);
        merchantCallbackRepository.save(targetCallback);
        MerchantCallback callback = new MerchantCallback();
        callback.setMerchant(Merchant.ASGARD);
        Order order = getDummyOrder(client);
        callback.setOrder(order);
        fillFields(callback);
        merchantCallbackRepository.save(callback);

        Page<MerchantCallbackResponse> callbacks = merchantCallbackService.findAll(
                MerchantCallbackSpecifications.matches(new MerchantCallbackFilter(
                        null, merchant, null, null, null
                )),
                PageRequest.of(0, 20),
                MerchantCallbackResponse.class
        );

        assertEquals(1, callbacks.getTotalElements());
        assertEquals(targetCallback.getId(), callbacks.getContent().getFirst().id());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "bb7ec0c6-9f7b-497f-8c18-06ecba259f49", "1259634"
    })
    void findAll_shouldFindCallbackByMerchantOrderId(String merchantOrderId) {
        Client client = getDummyClient();
        MerchantCallback targetCallback = new MerchantCallback();
        targetCallback.setMerchantOrderId(merchantOrderId);
        Order targetOrder = getDummyOrder(client);
        targetCallback.setOrder(targetOrder);
        fillFields(targetCallback);
        merchantCallbackRepository.save(targetCallback);
        MerchantCallback callback = new MerchantCallback();
        callback.setMerchantOrderId(merchantOrderId + "test");
        Order order = getDummyOrder(client);
        callback.setOrder(order);
        fillFields(callback);
        merchantCallbackRepository.save(callback);

        Page<MerchantCallbackResponse> callbacks = merchantCallbackService.findAll(
                MerchantCallbackSpecifications.matches(new MerchantCallbackFilter(
                        null, null, merchantOrderId, null, null
                )),
                PageRequest.of(0, 20),
                MerchantCallbackResponse.class
        );

        assertEquals(1, callbacks.getTotalElements());
        assertEquals(targetCallback.getId(), callbacks.getContent().getFirst().id());
    }

    @ParameterizedTest
    @ValueSource(longs = {
            1790262068808L, 1790262062000L
    })
    void findAll_shouldFindCallbackByCreatedAtFrom(long millis) {
        Client client = getDummyClient();
        MerchantCallback targetCallback = new MerchantCallback();
        targetCallback.setCreatedAt(Instant.ofEpochMilli(millis + 50000L));
        Order targetOrder = getDummyOrder(client);
        targetCallback.setOrder(targetOrder);
        fillFields(targetCallback);
        merchantCallbackRepository.save(targetCallback);
        MerchantCallback callback = new MerchantCallback();
        callback.setCreatedAt(Instant.ofEpochMilli(millis - 2000L));
        Order order = getDummyOrder(client);
        callback.setOrder(order);
        fillFields(callback);
        merchantCallbackRepository.save(callback);

        Page<MerchantCallbackResponse> callbacks = merchantCallbackService.findAll(
                MerchantCallbackSpecifications.matches(new MerchantCallbackFilter(
                        null, null, null, Instant.ofEpochMilli(millis), null
                )),
                PageRequest.of(0, 20),
                MerchantCallbackResponse.class
        );

        assertEquals(1, callbacks.getTotalElements());
        assertEquals(targetCallback.getId(), callbacks.getContent().getFirst().id());
    }

    @ParameterizedTest
    @ValueSource(longs = {
            1790262068808L, 1790262062000L
    })
    void findAll_shouldFindCallbackByCreatedAtTo(long millis) {
        Client client = getDummyClient();
        MerchantCallback targetCallback = new MerchantCallback();
        targetCallback.setCreatedAt(Instant.ofEpochMilli(millis - 50000L));
        Order targetOrder = getDummyOrder(client);
        targetCallback.setOrder(targetOrder);
        fillFields(targetCallback);
        merchantCallbackRepository.save(targetCallback);
        MerchantCallback callback = new MerchantCallback();
        callback.setCreatedAt(Instant.ofEpochMilli(millis + 2000L));
        Order order = getDummyOrder(client);
        callback.setOrder(order);
        fillFields(callback);
        merchantCallbackRepository.save(callback);

        Page<MerchantCallbackResponse> callbacks = merchantCallbackService.findAll(
                MerchantCallbackSpecifications.matches(new MerchantCallbackFilter(
                        null, null, null, null, Instant.ofEpochMilli(millis)
                )),
                PageRequest.of(0, 20),
                MerchantCallbackResponse.class
        );

        assertEquals(1, callbacks.getTotalElements());
        assertEquals(targetCallback.getId(), callbacks.getContent().getFirst().id());
    }
}