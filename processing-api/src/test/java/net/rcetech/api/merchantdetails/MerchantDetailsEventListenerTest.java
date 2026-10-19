package net.rcetech.api.merchantdetails;

import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.orders.OrderCanceledEvent;
import net.rcetech.meta.orders.RequestMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tgb.cryptoexchange.commons.enums.Merchant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MerchantDetailsEventListener.class, DetailsMapperImpl.class})
class MerchantDetailsEventListenerTest {

    @Container
    static MySQLContainer mySQLContainer = new MySQLContainer("mysql:8.0.46");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mySQLContainer::getUsername);
        registry.add("spring.datasource.password", mySQLContainer::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:database/migration");
    }

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @MockitoBean
    private ApiMerchantDetailsGrpcService apiMerchantDetailsGrpcService;

    @ParameterizedTest
    @CsvSource({
            "ALFA_TEAM,a8f1c2d4-5e6b-4c7a-9d8e-1f2a3b4c5d6e,CARD",
            "PAYSCROW,58213,SBP"
    })
    @DisplayName("После коммита транзакции должна быть вызвана отмена ордера в merchant-details с данными из события.")
    void orderCanceled_shouldCancelOrderInMerchantDetailsAfterCommit(Merchant merchant, String merchantOrderId,
                                                                     RequestMethod method) {
        eventPublisher.publishEvent(new OrderCanceledEvent(merchant, merchantOrderId, method));
        verifyNoInteractions(apiMerchantDetailsGrpcService);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        verify(apiMerchantDetailsGrpcService).cancelOrder(new CancelOrderDTO(merchant, merchantOrderId, method));
    }

    @ParameterizedTest
    @CsvSource({
            "STORM_TRADE,3c9e7a10-2b4d-4f6e-8a1c-0d2e4f6a8b9c,CARD",
            "ALFA_TEAM,77104,SBP"
    })
    @DisplayName("Отмена ордера в merchant-details не должна вызываться при откате транзакции.")
    void orderCanceled_shouldNotCancelOrderInMerchantDetailsAfterRollback(Merchant merchant, String merchantOrderId,
                                                                          RequestMethod method) {
        eventPublisher.publishEvent(new OrderCanceledEvent(merchant, merchantOrderId, method));
        TestTransaction.flagForRollback();
        TestTransaction.end();
        verifyNoInteractions(apiMerchantDetailsGrpcService);
    }

    @ParameterizedTest
    @CsvSource({
            "PAYSCROW,e4b7d1a2-9c3f-4e8a-b6d5-2a1c0f9e8d7b,SBP",
            "STORM_TRADE,10432,CARD"
    })
    @DisplayName("Ошибка при отмене ордера в merchant-details не должна пробрасываться наружу.")
    void orderCanceled_shouldNotThrowIfCancelOrderFailed(Merchant merchant, String merchantOrderId,
                                                         RequestMethod method) {
        doThrow(new BaseException("Непредвиденная ошибка: UNAVAILABLE"))
                .when(apiMerchantDetailsGrpcService).cancelOrder(any());
        eventPublisher.publishEvent(new OrderCanceledEvent(merchant, merchantOrderId, method));
        TestTransaction.flagForCommit();
        assertDoesNotThrow(TestTransaction::end);
        verify(apiMerchantDetailsGrpcService).cancelOrder(new CancelOrderDTO(merchant, merchantOrderId, method));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "null,null,null",
            "ALFA_TEAM,null,CARD",
            "null,58213,SBP"
    }, nullValues = "null")
    @DisplayName("Отмена ордера в merchant-details не должна вызываться, если мерчант или идентификатор ордера мерчанта не заданы.")
    void orderCanceled_shouldNotCancelOrderInMerchantDetailsIfMerchantDataMissing(Merchant merchant,
                                                                                  String merchantOrderId,
                                                                                  RequestMethod method) {
        eventPublisher.publishEvent(new OrderCanceledEvent(merchant, merchantOrderId, method));
        TestTransaction.flagForCommit();
        assertDoesNotThrow(TestTransaction::end);
        verifyNoInteractions(apiMerchantDetailsGrpcService);
    }
}
