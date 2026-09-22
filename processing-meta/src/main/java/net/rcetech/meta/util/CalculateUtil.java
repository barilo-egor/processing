package net.rcetech.meta.util;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.RoundingMode;

@UtilityClass
public class CalculateUtil {

    /**
     * Вычитание процента комиссии из суммы
     * @param amount сумма из которой будет вычтена комиссия
     * @param percent процент(как число от 0 до 100) комиссии
     * @return результат вычисления
     */
    public static Integer subtractPercentCommission(Integer amount, BigDecimal percent) {
        BigDecimal decimalAmount = new BigDecimal(amount);
        BigDecimal amountToSubtract = decimalAmount.multiply(percent.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP));
        return decimalAmount.subtract(amountToSubtract).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    public static Integer convert(Integer amount, BigDecimal rate) {
        BigDecimal decimalAmount = new BigDecimal(amount);
        return decimalAmount.divide(rate, RoundingMode.HALF_UP).setScale(0, RoundingMode.HALF_UP).intValue();
    }
}
