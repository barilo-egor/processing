package net.rcetech.clients.exceptions;

import lombok.Getter;

@Getter
public class InvalidApiKeyException extends RuntimeException {

    private final String field;

    private final String description;

    public InvalidApiKeyException() {
        super("User not found.");
        this.field = "apiKey";
        this.description = "ApiKey is invalid.";
    }

}
