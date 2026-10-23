package net.rcetech.orders.callback;

import lombok.Getter;
import net.rcetech.domain.model.orders.ClientCallback;

@Getter
public class CallbackSendException extends RuntimeException {

    private final transient ClientCallback callback;

    public CallbackSendException(ClientCallback callback, Throwable cause) {
        super(cause.getMessage(), cause);
        this.callback = callback;
    }
}
