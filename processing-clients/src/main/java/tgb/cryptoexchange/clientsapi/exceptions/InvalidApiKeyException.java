package tgb.cryptoexchange.clientsapi.exceptions;

import com.google.rpc.Code;
import lombok.Getter;

import static com.google.rpc.Code.INVALID_ARGUMENT;

@Getter
public class InvalidApiKeyException extends RuntimeException implements CustomException {

    private final Code errorCode;

    private final String field;

    private final String description;

    public InvalidApiKeyException() {
        super("User not found.");
        this.field = "apiKey";
        this.errorCode = INVALID_ARGUMENT;
        this.description = "ApiKey is invalid.";
    }

}
