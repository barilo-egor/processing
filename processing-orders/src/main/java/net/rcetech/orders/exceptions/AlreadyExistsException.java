package net.rcetech.orders.exceptions;

import lombok.Getter;

@Getter
public class AlreadyExistsException extends RuntimeException implements CustomException {

    private final String field;

    private final String description;

    public AlreadyExistsException(final String field) {
        super("Bad request.");
        this.field = field;
        this.description = "Should be unique.";
    }

}
