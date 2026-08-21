package net.rcetech.meta;

import net.rcetech.meta.exception.BaseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.net.URI;
import java.time.Instant;

@ControllerAdvice
public class GlobalControllerAdvice {

    private static final String TIMESTAMP = "timestamp";

    @ExceptionHandler({ BaseException.class, Exception.class })
    public ProblemDetail handleInternalConfigError(BaseException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problemDetail.setTitle(ex.getMessage());
        problemDetail.setType(URI.create("/errors/internal-server-error"));
        problemDetail.setProperty(TIMESTAMP, Instant.now());
        return problemDetail;
    }

    @ExceptionHandler({ AuthorizationDeniedException.class })
    public ProblemDetail handleAccessDenied(AuthorizationDeniedException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problemDetail.setTitle("Access Denied");
        problemDetail.setType(URI.create("/docs/errors/access-denied"));
        problemDetail.setProperty(TIMESTAMP, Instant.now());
        return problemDetail;
    }

}
