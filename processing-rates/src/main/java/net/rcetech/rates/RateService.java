package net.rcetech.rates;

import net.rcetech.meta.exception.BaseException;
import net.rcetech.rates.consumer.RateConsumer;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class RateService {

    private final Map<RatePair, RateConsumer> ratesConsumers = new EnumMap<>(RatePair.class);

    public RateService(List<RateConsumer> consumers) {
        for (RateConsumer rateConsumer: consumers) {
            ratesConsumers.put(rateConsumer.getPair(), rateConsumer);
        }
    }

    public BigDecimal getRate(RatePair pair) {
        if (!ratesConsumers.containsKey(pair)) {
            throw new BaseException("Не найден получатель курса для пары " + pair.name());
        }
        return ratesConsumers.get(pair).consume();
    }
}
