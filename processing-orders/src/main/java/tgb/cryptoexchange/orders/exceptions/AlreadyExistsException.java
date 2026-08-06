package tgb.cryptoexchange.orders.exceptions;

import com.google.rpc.Code;
import lombok.Getter;

import static com.google.rpc.Code.INVALID_ARGUMENT;

@Getter
public class AlreadyExistsException extends RuntimeException implements CustomException {

    private final Code errorCode;

    private final String field;

    private final String description;

    public AlreadyExistsException(final String field) {
        super("Bad request.");
        this.errorCode = INVALID_ARGUMENT;
        this.field = field;
        this.description = "Should be unique.";
    }

}
