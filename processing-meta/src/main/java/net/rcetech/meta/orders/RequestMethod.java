package net.rcetech.meta.orders;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum RequestMethod {
    CARD("Карта"),
    SBP("СБП"),
    QR("QR");

    private final String description;
}
