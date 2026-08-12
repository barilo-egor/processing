package net.rcetech.meta.clients.exception;

import lombok.Getter;


@Getter
public class FieldNotBeEmptyException extends RuntimeException {

    private final String field;

    private final String description;

    public FieldNotBeEmptyException(final String field) {
        super("Bad request.");
        this.field = field;
        this.description = "Should not be empty.";
    }

}
