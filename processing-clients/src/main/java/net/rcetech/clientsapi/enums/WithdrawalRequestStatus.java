package net.rcetech.clientsapi.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum WithdrawalRequestStatus {

    NEW("Создана");

    private final String description;

}
