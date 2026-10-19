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

class RandomDetailsGeneratorTest {

    private final RandomDetailsGenerator randomDetailsGenerator = new RandomDetailsGenerator();

    @ParameterizedTest
    @MethodSource("methodsArguments")
    @DisplayName("Метод реквизитов должен выбираться из методов, переданных в запросе.")
    void generate_shouldChooseMethodFromRequest(Set<RequestMethod> methods) {
        for (int i = 0; i < 20; i++) {
            ApiDetailsResponse response = randomDetailsGenerator.generate(request(methods));
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
            String details = randomDetailsGenerator.generate(request(Set.of(method))).details().details();
            assertTrue(details.matches(regex), details);
        }
    }

    @ParameterizedTest
    @EnumSource(RequestMethod.class)
    @DisplayName("Ответ должен содержать мерчанта, банк, уникальный идентификатор ордера мерчанта и не менять сумму.")
    void generate_shouldFillRequiredFields(RequestMethod method) {
        ApiDetailsResponse first = randomDetailsGenerator.generate(request(Set.of(method)));
        ApiDetailsResponse second = randomDetailsGenerator.generate(request(Set.of(method)));
        assertAll(
                () -> assertNotNull(first.requestId()),
                () -> assertNotNull(first.merchant()),
                () -> assertNotNull(first.orderStatus()),
                () -> assertNotNull(first.details().bank()),
                () -> assertNull(first.amount()),
                () -> assertNotEquals(first.orderId(), second.orderId())
        );
    }

    private CreateOrderRequest request(Set<RequestMethod> methods) {
        return new CreateOrderRequest(UUID.randomUUID().toString(), 1000, methods, null, "1235153208");
    }
}
