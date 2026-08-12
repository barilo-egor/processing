package net.rcetech.meta.orders.exception;

import lombok.Getter;

@Getter
public class NotFoundException extends RuntimeException implements CustomException {
    private final String field;

    private final String description;

    public NotFoundException(final String fieldId) {
        super("Record not found for the provided ID.");
        this.field = fieldId;
        this.description = null;
    }

}
