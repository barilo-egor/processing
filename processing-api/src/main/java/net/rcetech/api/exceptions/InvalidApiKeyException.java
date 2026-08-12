package net.rcetech.api.exceptions;

import lombok.Getter;

@Getter
public class InvalidApiKeyException extends RuntimeException {

    public InvalidApiKeyException(String message) {
        super(message);
    }

}
