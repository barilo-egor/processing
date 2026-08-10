package net.rcetech.clients.exceptions;

import lombok.Getter;

@Getter
public class NotFoundException extends RuntimeException implements CustomException {

    private final String field;

    private final String description;

    public NotFoundException(final String field) {
        super("Bad request.");
        this.field = field;
        this.description = "Record not found for the provided ID.";
    }

}
