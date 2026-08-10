package net.rcetech.details.exceptions;

import lombok.Getter;

@Getter
public class InvalidApiKeyException extends RuntimeException {

    public InvalidApiKeyException() {
        super("User not found.");
    }

}
