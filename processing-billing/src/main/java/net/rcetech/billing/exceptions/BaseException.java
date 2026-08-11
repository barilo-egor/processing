package net.rcetech.billing.exceptions;

import lombok.Getter;

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
