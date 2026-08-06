package tgb.cryptoexchange.clientsapi.exceptions;

public class BodyMappingException extends RuntimeException {

    public BodyMappingException() {
    }

    public BodyMappingException(String message) {
        super(message);
    }

    public BodyMappingException(String message, Throwable cause) {
        super(message, cause);
    }

}
