package tgb.cryptoexchange.processingdetailsapi.exceptions;

import lombok.Getter;

@Getter
public class BaseException extends RuntimeException {

    public BaseException(String message) {
        super(message);
    }

}
