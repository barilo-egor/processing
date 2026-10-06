package net.rcetech.rates;

import net.rcetech.meta.exception.BaseException;
import net.rcetech.rates.consumer.RateConsumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateServiceTest {

    static class TestRateConsumer implements RateConsumer {

        private final BigDecimal rate;

        TestRateConsumer(BigDecimal rate) {
            this.rate = rate;
        }

        @Override
        public BigDecimal consume() {
            return rate;
        }

        @Override
        public RatePair getPair() {
            return RatePair.USDT_RUB;
        }
    }

    @Test
    @DisplayName("Метод должен бросить BaseException, если получатель курса для пары не найден.")
    void getRate_shouldThrowBaseExceptionIfConsumerForPairDoesNotExist() {
        RateService rateService = new RateService(List.of());
        assertThrows(BaseException.class, () -> rateService.getRate(RatePair.USDT_RUB));
    }

    @ParameterizedTest
    @ValueSource(doubles = {80.0, 78.82, 78.01})
    @DisplayName("Метод должен вернуть курс, возвращенный получателем курса.")
    void getRate_shouldReturnRate(double rate) {
        RateService rateService = new RateService(List.of(new TestRateConsumer(new BigDecimal(rate))));
        assertEquals(new BigDecimal(rate), rateService.getRate(RatePair.USDT_RUB));
    }

}