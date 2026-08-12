package net.rcetech.clients.exceptions;

import lombok.Getter;

@Getter
public class BaseException extends RuntimeException implements net.rcetech.meta.CustomException {

    private final String field;

    private final String description;

    public BaseException(String message) {
        super(message);
        this.field = null;
        this.description = null;
    }

}
