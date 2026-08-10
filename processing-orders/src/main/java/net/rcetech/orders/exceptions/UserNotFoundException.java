package net.rcetech.orders.exceptions;

import lombok.Getter;

@Getter
public class UserNotFoundException extends RuntimeException implements CustomException {

    private final String field;

    private final String description;

    public UserNotFoundException() {
        super("User not found.");
        this.field = null;
        this.description = null;
    }

}
