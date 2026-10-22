package net.rcetech.api.merchantdetails;

import net.rcetech.api.dto.CreateOrderRequest;
import net.rcetech.meta.orders.RequestMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class RandomDetailsReceiverTest {

    private final RandomDetailsReceiver randomDetailsReceiver = new RandomDetailsReceiver();

    @ParameterizedTest
    @MethodSource("methodsArguments")
    @DisplayName("Метод реквизитов должен выбираться из методов, переданных в запросе.")
    void generate_shouldChooseMethodFromRequest(Set<RequestMethod> methods) {
        for (int i = 0; i < 20; i++) {
            ApiDetailsResponse response = details(methods);
            assertTrue(methods.contains(response.details().requestMethod()));
        }
    }

    static Stream<Arguments> methodsArguments() {
        return Stream.of(
                Arguments.of(Set.of(RequestMethod.CARD)),
                Arguments.of(Set.of(RequestMethod.SBP)),
                Arguments.of(Set.of(RequestMethod.CARD, RequestMethod.SBP))
        );
    }

    @ParameterizedTest
    @EnumSource(RequestMethod.class)
    @DisplayName("Реквизиты должны соответствовать формату метода: номер карты или номер телефона.")
    void generate_shouldGenerateDetailsMatchingMethod(RequestMethod method) {
        String regex = RequestMethod.CARD.equals(method) ? "\\d{4} \\d{4} \\d{4} \\d{4}" : "\\+79\\d{9}";
        for (int i = 0; i < 20; i++) {
            String details = details(Set.of(method)).details().details();
            assertTrue(details.matches(regex), details);
        }
    }

    @ParameterizedTest
    @EnumSource(RequestMethod.class)
    @DisplayName("Ответ должен содержать мерчанта, банк, уникальный идентификатор ордера мерчанта и не менять сумму.")
    void generate_shouldFillRequiredFields(RequestMethod method) {
        ApiDetailsResponse first = details(Set.of(method));
        ApiDetailsResponse second = details(Set.of(method));
        assertAll(
                () -> assertNotNull(first.requestId()),
                () -> assertNotNull(first.merchant()),
                () -> assertNotNull(first.orderStatus()),
                () -> assertNotNull(first.details().bank()),
                () -> assertNull(first.amount()),
                () -> assertTrue(first.orderId().startsWith(RandomDetailsReceiver.TEST_ORDER_ID_PREFIX)),
                () -> assertNotEquals(first.orderId(), second.orderId())
        );
    }

    private ApiDetailsResponse details(Set<RequestMethod> methods) {
        CreateOrderRequest request = new CreateOrderRequest(UUID.randomUUID().toString(), 1000, methods, null,
                "1235153208");
        return randomDetailsReceiver.getDetails(UUID.randomUUID(), UUID.randomUUID(), request, 30).orElseThrow();
    }
}
