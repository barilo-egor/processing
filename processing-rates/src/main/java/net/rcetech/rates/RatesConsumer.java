package net.rcetech.rates;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class RatesConsumer {

    // TODO реализовать получение курсов
    public BigDecimal consume() {
        return new BigDecimal("70.85");
    }
}
