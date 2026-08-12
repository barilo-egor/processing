package net.rcetech.meta.exception;

import lombok.Getter;

@Getter
public class NotFoundException extends RuntimeException {

    private final String field;

    private final String description;

    public NotFoundException(final String field) {
        super("Bad request.");
        this.field = field;
        this.description = "Record not found for the provided ID.";
    }

}
