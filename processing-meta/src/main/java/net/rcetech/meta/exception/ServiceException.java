package net.rcetech.meta.exception;

/**
 * Сообщения данного исключения выводятся в ProblemDetail в поле description. Если сообщение носит технический характер,
 * то следует использовать другое исклюкениче.
 * @see {@link net.rcetech.meta.GlobalControllerAdvice}
 */
public class ServiceException extends RuntimeException {

    public ServiceException() {
        super();
    }

    public ServiceException(String message) {
        super(message);
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
