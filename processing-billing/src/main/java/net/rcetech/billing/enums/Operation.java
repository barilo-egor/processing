package net.rcetech.billing.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Operation {

    CREDIT("Зачисление"),
    DEBIT("Списание");

    private final String description;

}
