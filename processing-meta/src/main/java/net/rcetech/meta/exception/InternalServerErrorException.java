package net.rcetech.meta.exception;

/**
 * Внутренняя ошибка сервера, сообщение которой предназначено пользователю. Ответ отдаётся со статусом 500,
 * сообщение выводится в ProblemDetail в поле description, поэтому оно не должно содержать технических подробностей.
 *
 * @see net.rcetech.meta.GlobalControllerAdvice
 */
public class InternalServerErrorException extends RuntimeException {

    public InternalServerErrorException(String message) {
        super(message);
    }

    public InternalServerErrorException(String message, Throwable cause) {
        super(message, cause);
    }
}
