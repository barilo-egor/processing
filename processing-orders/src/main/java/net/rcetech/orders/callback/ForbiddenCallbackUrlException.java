package net.rcetech.orders.callback;

public class ForbiddenCallbackUrlException extends RuntimeException {

    public ForbiddenCallbackUrlException(String message) {
        super(message);
    }
}
