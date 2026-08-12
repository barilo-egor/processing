package net.rcetech.meta.clients.exception;

import lombok.Getter;
import net.rcetech.meta.CustomException;

@Getter
public class FieldNotBeEmptyException extends RuntimeException implements CustomException {

    private final String field;

    private final String description;

    public FieldNotBeEmptyException(final String field) {
        super("Bad request.");
        this.field = field;
        this.description = "Should not be empty.";
    }

}
