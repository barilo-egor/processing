package net.rcetech.clientsapi.exceptions;

import com.google.rpc.Code;
import lombok.Getter;

import static com.google.rpc.Code.INVALID_ARGUMENT;

@Getter
public class PasswordValidationException extends RuntimeException implements CustomException {

    private final Code errorCode;

    private final String field;

    private final String description;

    public PasswordValidationException() {
        super("Bad request.");
        this.errorCode = INVALID_ARGUMENT;
        this.field = "password";
        this.description = "Password does not meet the requirements. It must be at least 8 characters long and include uppercase and lowercase letters, digits, and special characters.";
    }

}
