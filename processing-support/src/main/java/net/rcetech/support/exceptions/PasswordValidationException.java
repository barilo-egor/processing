package net.rcetech.support.exceptions;

import lombok.Getter;

@Getter
public class PasswordValidationException extends RuntimeException {

    private final String field;

    private final String description;

    public PasswordValidationException() {
        super("Bad request.");
        this.field = "password";
        this.description = "Password does not meet the requirements. It must be at least 8 characters long and include uppercase and lowercase letters, digits, and special characters.";
    }

}
