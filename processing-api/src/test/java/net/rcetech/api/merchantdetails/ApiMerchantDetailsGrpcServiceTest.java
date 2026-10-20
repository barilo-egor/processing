package net.rcetech.api.merchantdetails;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.rcetech.api.dto.CreateOrderRequest;
import net.rcetech.grpc.generated.ApiDetailsRequestServiceGrpc;
import net.rcetech.grpc.generated.DetailsGrpc;
import net.rcetech.grpc.generated.DetailsResponseGrpc;
import net.rcetech.domain.service.incidents.IncidentService;
import net.rcetech.meta.incidents.IncidentType;
import net.rcetech.meta.incidents.dto.DetailsSearchErrorData;
import net.rcetech.meta.orders.RequestMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiMerchantDetailsGrpcServiceTest {

    @Mock
    private ApiDetailsRequestServiceGrpc.ApiDetailsRequestServiceBlockingStub detailsBlockingStub;

    @Spy
    private DetailsMapper detailsMapper = Mappers.getMapper(DetailsMapper.class);

    @Mock
    private IncidentService incidentService;

    private ApiMerchantDetailsGrpcService apiMerchantDetailsGrpcService;

    @BeforeEach
    void setUp() {
        apiMerchantDetailsGrpcService =  new ApiMerchantDetailsGrpcService(
                detailsMapper, detailsBlockingStub, "processing", incidentService
        );
    }


    @ParameterizedTest
    @CsvSource("""
            ALFA_TEAM,SUCCESS,5215,CARD,1234 1234 1234 1234,Сбербанк
            PAYSCROW,PROCESS,2443,SBP,+79851235423,МТС
            """)
    @DisplayName("Метод должен вернуть валидный ответ.")
    void getDetails_shouldReturnResponse(Merchant merchant, String merchantOrderStatus, Integer amount,
                                         RequestMethod requestMethod, String details, String bank) {
        UUID requestId = UUID.randomUUID();
        UUID merchantOrderId = UUID.randomUUID();
        DetailsResponseGrpc detailsResponseGrpc = DetailsResponseGrpc.newBuilder()
                .setRequestId(requestId.toString())
                .setMerchant(merchant.name())
                .setOrderId(merchantOrderId.toString())
                .setOrderStatus(merchantOrderStatus)
                .setDetails(DetailsGrpc.newBuilder()
                        .setRequestMethod(requestMethod.name())
                        .setDetails(details)
                        .setBank(bank)
                        .build())
                .setAmount(amount)
                .build();
        when(detailsBlockingStub.detailsRequest(any())).thenReturn(detailsResponseGrpc);
        UUID clientId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID internalId = UUID.randomUUID();
        ApiDetailsResponse actual = apiMerchantDetailsGrpcService.getDetails(clientId, orderId,
                new CreateOrderRequest(
                        internalId.toString(), 5042, Set.of(RequestMethod.CARD),
                        "https://example.com/callback", "534690"
                ), 60
        ).orElseThrow();
        assertAll(
                () -> assertEquals(requestId.toString(), actual.requestId()),
                () -> assertEquals(merchant, actual.merchant()),
                () -> assertEquals(merchantOrderId.toString(), actual.orderId()),
                () -> assertEquals(merchantOrderStatus, actual.orderStatus()),
                () -> assertEquals(amount, actual.amount()),
                () -> assertEquals(requestMethod, actual.details().requestMethod()),
                () -> assertEquals(details, actual.details().details()),
                () -> assertEquals(bank, actual.details().bank())

        );
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    @DisplayName("Метод должен вернуть пустой Optional без инцидента, если реквизиты не найдены или поиск отменён.")
    void getDetails_shouldReturnEmptyWithoutIncidentIfNotFoundOrCancelled(int statusValue) {
        when(detailsBlockingStub.detailsRequest(any()))
                .thenThrow(new StatusRuntimeException(Status.fromCodeValue(statusValue)));
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
                UUID.randomUUID().toString(), 5042, Set.of(RequestMethod.CARD),
                "https://example.com/callback", "534690"
        );
        UUID clientId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        assertTrue(apiMerchantDetailsGrpcService.getDetails(clientId, orderId, createOrderRequest, 30).isEmpty());
        verifyNoInteractions(incidentService);
    }

    @ParameterizedTest
    @ValueSource(ints = {
            3, 6, 14
    })
    @DisplayName("Метод должен вернуть пустой Optional и создать инцидент, если статус ответа GRPC не ожидаем.")
    void getDetails_shouldReturnEmptyAndSaveIncidentIfUnknownStatus(int statusValue) {
        Status status = Status.fromCodeValue(statusValue);
        when(detailsBlockingStub.detailsRequest(any())).thenThrow(new StatusRuntimeException(status));
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
                UUID.randomUUID().toString(), 5042, Set.of(RequestMethod.CARD),
                "https://example.com/callback", "534690"
        );
        UUID clientId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        assertTrue(apiMerchantDetailsGrpcService.getDetails(clientId, orderId, createOrderRequest, 30).isEmpty());
        verify(incidentService).saveIfNoUnresolved(eq(IncidentType.DETAILS_SEARCH_ERROR),
                eq(new DetailsSearchErrorData(orderId, "Неизвестная ошибка GRPC " + statusValue)), anyLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Illegal State", "Connection refused"})
    @DisplayName("Метод должен вернуть пустой Optional и создать инцидент, если произошла непредвиденная ошибка при попытке получить реквизиты.")
    void getDetails_shouldReturnEmptyAndSaveIncidentIfUnknownException(String message) {
        when(detailsBlockingStub.detailsRequest(any())).thenThrow(new IllegalStateException(message));
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
                UUID.randomUUID().toString(), 5042, Set.of(RequestMethod.CARD),
                "https://example.com/callback", "534690"
        );
        UUID clientId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        assertTrue(apiMerchantDetailsGrpcService.getDetails(clientId, orderId, createOrderRequest, 30).isEmpty());
        verify(incidentService).saveIfNoUnresolved(eq(IncidentType.DETAILS_SEARCH_ERROR),
                eq(new DetailsSearchErrorData(orderId, "Непредвиденная ошибка: " + message)), anyLong());
    }
}
