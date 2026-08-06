package tgb.cryptoexchange.clientsapi.exceptions;

import com.google.rpc.Code;
import lombok.Getter;

import static com.google.rpc.Code.INVALID_ARGUMENT;

@Getter
public class UnauthorizedException extends RuntimeException implements CustomException {

    private final Code errorCode;

    private final String field;

    private final String description;

    public UnauthorizedException(String message) {
        super(message);
        this.errorCode = INVALID_ARGUMENT;
        this.field = null;
        this.description = null;
    }

}
