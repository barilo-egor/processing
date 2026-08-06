package tgb.cryptoexchange.orders.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TransactionType {

    ORDER_CONFIRMATION("Подтверждение ордера"),
    CLIENT_WITHDRAWAL("Вывод по инициативе клиента");

    private final String description;

}
