package net.rcetech.meta.exception;

import lombok.Getter;

@Getter
public class BaseException extends RuntimeException {

    private final Long timestamp;

    public BaseException(String message) {
        super(message);
        this.timestamp = null;
    }

    public BaseException(String message, Long timestamp) {
        super(message);
        this.timestamp = timestamp;
    }

    public BaseException(String message, Throwable cause) {
        super(message, cause);
        this.timestamp = null;
    }
}
