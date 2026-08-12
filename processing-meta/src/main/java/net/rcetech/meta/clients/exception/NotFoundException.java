package net.rcetech.meta.clients.exception;

import lombok.Getter;

@Getter
public class NotFoundException extends RuntimeException implements net.rcetech.meta.CustomException {

    private final String field;

    private final String description;

    public NotFoundException(final String field) {
        super("Bad request.");
        this.field = field;
        this.description = "Record not found for the provided ID.";
    }

}
