package net.rcetech.meta.orders;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum RequestMethod {
    CARD("Карта"),
    SBP("СБП");

    private final String description;
}
