package net.rcetech.orders.exceptions;

import com.google.rpc.Code;
import lombok.Getter;

@Getter
public class UserNotFoundException extends RuntimeException implements CustomException {

    private final Code errorCode;

    private final String field;

    private final String description;

    public UserNotFoundException() {
        super("User not found.");
        this.errorCode = com.google.rpc.Code.NOT_FOUND;
        this.field = null;
        this.description = null;
    }

}
