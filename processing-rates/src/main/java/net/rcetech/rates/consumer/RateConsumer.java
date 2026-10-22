package net.rcetech.rates.consumer;

import net.rcetech.rates.RateConsumeException;
import net.rcetech.rates.RatePair;

import java.math.BigDecimal;

/**
 * Общий интерфейс для получателей валют по валютной паре. При создании спринг бина типа {@link RateConsumer} в контекст
 * станет доступно получение курса в методе {@link net.rcetech.rates.RateService#getRate(RatePair)}.
 */
public interface RateConsumer {

    /**
     * Получение курса с биржи.
     * @return текущий курс валютной пары
     * @throws RateConsumeException в случае, если не удалось получить курс
     */
    BigDecimal consume() throws RateConsumeException;

    /**
     * Валютная пара, курс которой получается в методе {@link #consume()}
     * @return валютная пара
     */
    RatePair getPair();
}
