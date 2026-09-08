package net.rcetech.api.service;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.rcetech.api.dto.ApiDetailsResponse;
import net.rcetech.api.dto.CreateOrderRequest;
import net.rcetech.api.mapper.DetailsMapper;
import net.rcetech.grpc.generated.ApiDetailsRequestServiceGrpc;
import net.rcetech.grpc.generated.DetailsGrpc;
import net.rcetech.grpc.generated.DetailsResponseGrpc;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.exception.MerchantDetailsNotFoundException;
import net.rcetech.meta.orders.RequestMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiMerchantDetailsGrpcServiceTest {

    @Mock
    private ApiDetailsRequestServiceGrpc.ApiDetailsRequestServiceBlockingStub detailsBlockingStub;

    @Spy
    private DetailsMapper detailsMapper = Mappers.getMapper(DetailsMapper.class);

    @InjectMocks
    private ApiMerchantDetailsGrpcService apiMerchantDetailsGrpcService;

    @ParameterizedTest
    @CsvSource("""
            ALFA_TEAM,SUCCESS,5215,CARD,1234 1234 1234 1234,Сбербанк
            PAYSCROW,PROCESS,2443,SBP,+79851235423,МТС
            """)
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
        UUID orderId = UUID.randomUUID();
        UUID internalId = UUID.randomUUID();
        ApiDetailsResponse actual = apiMerchantDetailsGrpcService.getDetails(orderId,
                new CreateOrderRequest(
                        internalId.toString(), 5042, Set.of(RequestMethod.CARD), true,
                        "https://example.com/callback", "534690"
                )
        );
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

    @Test
    void getDetails_shouldThrowMerchantDetailsNotFoundExceptionIfNotFoundGrpcStatus() {
        when(detailsBlockingStub.detailsRequest(any())).thenThrow(new StatusRuntimeException(Status.NOT_FOUND));
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
                UUID.randomUUID().toString(), 5042, Set.of(RequestMethod.CARD), true,
                "https://example.com/callback", "534690"
        );
        UUID orderId = UUID.randomUUID();
        assertThrows(MerchantDetailsNotFoundException.class, () -> apiMerchantDetailsGrpcService.getDetails(orderId, createOrderRequest));
    }

    @ParameterizedTest
    @ValueSource(ints = {
            1, 3, 6
    })
    void getDetails_shouldThrowBaseExceptionIfUnknownStatus(int statusValue) {
        Status status = Status.fromCodeValue(statusValue);
        when(detailsBlockingStub.detailsRequest(any())).thenThrow(new StatusRuntimeException(status));
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
                UUID.randomUUID().toString(), 5042, Set.of(RequestMethod.CARD), true,
                "https://example.com/callback", "534690"
        );
        UUID orderId = UUID.randomUUID();
        assertThrows(BaseException.class, () -> apiMerchantDetailsGrpcService.getDetails(orderId, createOrderRequest),
                "Неизвестная ошибка GRPC " + statusValue);
    }

    @Test
    void getDetails_shouldThrowBaseExceptionIfUnknownException() {
        when(detailsBlockingStub.detailsRequest(any())).thenThrow(new IllegalStateException("Illegal State"));
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
                UUID.randomUUID().toString(), 5042, Set.of(RequestMethod.CARD), true,
                "https://example.com/callback", "534690"
        );
        UUID orderId = UUID.randomUUID();
        assertThrows(BaseException.class, () -> apiMerchantDetailsGrpcService.getDetails(orderId, createOrderRequest),
                "Непредвиденная ошибка: Illegal State");
    }
}