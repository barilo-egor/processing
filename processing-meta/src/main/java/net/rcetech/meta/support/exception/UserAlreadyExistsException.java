package net.rcetech.meta.support.exception;

import lombok.Getter;

@Getter
public class UserAlreadyExistsException extends RuntimeException {

    private final String field;

    private final String description;

    public UserAlreadyExistsException() {
        super("Bad request.");
        this.field = "username";
        this.description = "Username is already taken.";
    }

}
