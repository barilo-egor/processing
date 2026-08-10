package net.rcetech.support.exceptions;

import lombok.Getter;

@Getter
public class UserNotFoundException extends RuntimeException {

    private final String field;

    private final String description;

    public UserNotFoundException() {
        super("User not found.");
        this.field = null;
        this.description = null;
    }

}
