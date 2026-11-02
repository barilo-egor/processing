package net.rcetech.api.merchantdetails;

import net.rcetech.meta.orders.RequestMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetailsValidatorTest {

    private static final Set<RequestMethod> CARD_AND_SBP = Set.of(RequestMethod.CARD, RequestMethod.SBP);

    private ApiDetailsResponse response(Integer amount, RequestMethod method, String details, String bank) {
        return new ApiDetailsResponse(Merchant.ONLY_PAYS, "m-order-1", "PENDING", amount,
                new ApiDetailsResponse.Details(method, details, bank));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "null,CARD,2200 0000 0000 0000,Т-Банк",
            "2003,SBP,+79990000000,Сбербанк"
    }, nullValues = "null")
    @DisplayName("Корректные реквизиты должны проходить проверку.")
    void validate_shouldAcceptCompleteDetails(Integer amount, RequestMethod method, String details, String bank) {
        assertEquals(Optional.empty(), DetailsValidator.validate(response(amount, method, details, bank), CARD_AND_SBP));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("Реквизиты без банка должны отклоняться.")
    void validate_shouldRejectDetailsWithoutBank(String bank) {
        assertEquals(Optional.of("не указан банк"),
                DetailsValidator.validate(response(null, RequestMethod.SBP, "+79990000000", bank), CARD_AND_SBP));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://pay.example.com/form/1", "http://pay.example.com/form/1", " HTTPS://PAY.EXAMPLE.COM"})
    @DisplayName("Реквизиты, переданные ссылкой, должны отклоняться.")
    void validate_shouldRejectLinkDetails(String details) {
        assertEquals(Optional.of("реквизиты переданы ссылкой"),
                DetailsValidator.validate(response(null, RequestMethod.CARD, details, "Т-Банк"), CARD_AND_SBP));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Пустые реквизиты должны отклоняться.")
    void validate_shouldRejectEmptyDetails(String details) {
        assertEquals(Optional.of("пустые реквизиты"),
                DetailsValidator.validate(response(null, RequestMethod.CARD, details, "Т-Банк"), CARD_AND_SBP));
    }

    @Test
    @DisplayName("Реквизиты по незапрошенному методу должны отклоняться.")
    void validate_shouldRejectNotRequestedMethod() {
        assertEquals(Optional.of("метод оплаты SBP не входит в запрошенные"), DetailsValidator.validate(
                response(null, RequestMethod.SBP, "+79990000000", "Сбербанк"), Set.of(RequestMethod.CARD)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("Реквизиты с неположительной суммой должны отклоняться.")
    void validate_shouldRejectNonPositiveAmount(int amount) {
        assertEquals(Optional.of("некорректная сумма " + amount),
                DetailsValidator.validate(response(amount, RequestMethod.CARD, "2200 0000 0000 0000", "Т-Банк"),
                        CARD_AND_SBP));
    }

    @Test
    @DisplayName("Ответ без мерчанта, идентификатора ордера и реквизитов должен отклоняться со всеми причинами.")
    void validate_shouldJoinAllViolations() {
        Optional<String> actual = DetailsValidator.validate(
                new ApiDetailsResponse(null, " ", "PENDING", null, null), CARD_AND_SBP);
        assertEquals(Optional.of("не указан мерчант, не указан идентификатор ордера мерчанта, нет реквизитов"), actual);
    }

    @Test
    @DisplayName("Ссылка без банка должна отклоняться с обеими причинами.")
    void validate_shouldRejectLinkWithoutBank() {
        Optional<String> actual = DetailsValidator.validate(
                response(null, RequestMethod.CARD, "https://pay.example.com/form/1", null), CARD_AND_SBP);
        assertTrue(actual.isPresent());
        assertEquals("реквизиты переданы ссылкой, не указан банк", actual.get());
    }
}
