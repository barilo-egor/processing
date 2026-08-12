package net.rcetech.clients.exceptions;

import lombok.Getter;

@Getter
public class PasswordValidationException extends RuntimeException implements net.rcetech.meta.CustomException {

    private final String field;

    private final String description;

    public PasswordValidationException() {
        super("Bad request.");
        this.field = "password";
        this.description = "Password does not meet the requirements. It must be at least 8 characters long and include uppercase and lowercase letters, digits, and special characters.";
    }

}
