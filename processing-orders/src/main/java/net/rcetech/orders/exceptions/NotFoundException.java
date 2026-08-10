package net.rcetech.orders.exceptions;

import com.google.rpc.Code;
import lombok.Getter;

import static com.google.rpc.Code.NOT_FOUND;

@Getter
public class NotFoundException extends RuntimeException implements CustomException {

    private final Code errorCode;

    private final String field;

    private final String description;

    public NotFoundException(final String fieldId) {
        super("Record not found for the provided ID.");
        this.errorCode = NOT_FOUND;
        this.field = fieldId;
        this.description = null;
    }

}
