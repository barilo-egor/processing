package net.rcetech.clientsapi.exceptions;

import com.google.rpc.Code;
import lombok.Getter;

import static com.google.rpc.Code.INVALID_ARGUMENT;

@Getter
public class ClientAlreadyExistsException extends RuntimeException implements CustomException {

    private final Code errorCode;

    private final String field;

    private final String description;

    public ClientAlreadyExistsException() {
        super("Bad request.");
        this.errorCode = INVALID_ARGUMENT;
        this.field = "username";
        this.description = "Username is already taken.";
    }

}
