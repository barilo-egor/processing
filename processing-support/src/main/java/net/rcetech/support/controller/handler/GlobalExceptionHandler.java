package net.rcetech.support.controller.handler;

import net.rcetech.support.exceptions.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import net.rcetech.processingsupportusers.exceptions.*;

import java.net.URI;
import java.time.Instant;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String TIMESTAMP = "timestamp";

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ProblemDetail handleUserAlreadyExists(UserAlreadyExistsException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle(ex.getDescription());
        problemDetail.setType(URI.create("/errors/already-exists"));
        problemDetail.setProperty(TIMESTAMP, Instant.now());

        return problemDetail;
    }

    @ExceptionHandler(PasswordValidationException.class)
    public ProblemDetail handlePasswordValidation(PasswordValidationException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle(ex.getDescription());
        problemDetail.setType(URI.create("/errors/password-validation"));
        problemDetail.setProperty(TIMESTAMP, Instant.now());

        return problemDetail;
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail handleUnauthorized(UnauthorizedException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problemDetail.setTitle(ex.getMessage());
        problemDetail.setType(URI.create("/errors/unauthorized"));
        problemDetail.setProperty(TIMESTAMP, Instant.now());

        return problemDetail;
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleResourceNotFound(NotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problemDetail.setTitle(ex.getDescription());
        problemDetail.setType(URI.create("/errors/resource-not-found"));
        problemDetail.setProperty(TIMESTAMP, Instant.now());

        return problemDetail;
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(NotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problemDetail.setTitle(ex.getMessage());
        problemDetail.setType(URI.create("/errors/user-not-found"));
        problemDetail.setProperty(TIMESTAMP, Instant.now());

        return problemDetail;
    }

    @ExceptionHandler(BaseException.class)
    public ProblemDetail handleInternalConfigError(BaseException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problemDetail.setTitle(ex.getMessage());
        problemDetail.setType(URI.create("/errors/internal-server-error"));
        problemDetail.setProperty(TIMESTAMP, Instant.now());

        return problemDetail;
    }

}
