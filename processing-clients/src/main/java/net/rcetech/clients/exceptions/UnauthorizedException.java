package net.rcetech.clients.exceptions;

import lombok.Getter;

@Getter
public class UnauthorizedException extends RuntimeException {

    private final String field;

    private final String description;

    public UnauthorizedException(String message) {
        super(message);
        this.field = null;
        this.description = null;
    }

}
