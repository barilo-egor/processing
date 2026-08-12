package net.rcetech.orders.exceptions;

import lombok.Getter;
import net.rcetech.meta.orders.exception.CustomException;

@Getter
public class BaseException extends RuntimeException implements CustomException {

    private final String field;

    private final String description;

    public BaseException(String message) {
        super(message);
        this.field = null;
        this.description = null;
    }

}
