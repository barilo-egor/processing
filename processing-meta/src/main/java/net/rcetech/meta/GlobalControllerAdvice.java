package net.rcetech.meta;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import net.rcetech.meta.exception.BadRequestException;
import net.rcetech.meta.exception.BaseException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;

@ControllerAdvice
@NullMarked
@Slf4j
public class GlobalControllerAdvice extends ResponseEntityExceptionHandler {

    private static final String TIMESTAMP = "timestamp";
    private static final String DESCRIPTION = "description";

    @ExceptionHandler({ BaseException.class })
    public ProblemDetail handleBaseException(BaseException ex) {
        long epochMilli = Instant.now().toEpochMilli();
        log.error("{} Controller business error: {}", epochMilli, ex.getMessage(), ex);
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problemDetail.setTitle(ex.getMessage());
        problemDetail.setType(URI.create("/errors/internal-server-error"));
        problemDetail.setProperty(TIMESTAMP, epochMilli);
        return problemDetail;
    }

    @ExceptionHandler({ Exception.class })
    public ProblemDetail handleGenericException(Exception ex) {
        long epochMilli = Instant.now().toEpochMilli();
        log.error("{} Controller not handled error: {}", epochMilli, ex.getMessage(), ex);
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setType(URI.create("/errors/internal-server-error"));
        problemDetail.setProperty(TIMESTAMP, epochMilli);
        problemDetail.setProperty(DESCRIPTION, "Internal Server Error");
        return problemDetail;
    }

    @ExceptionHandler({ AuthorizationDeniedException.class })
    public ProblemDetail handleAccessDenied(AuthorizationDeniedException ex) {
        log.error("Authorization denied error: {}", ex.getMessage(), ex);
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problemDetail.setTitle("Access Denied");
        problemDetail.setType(URI.create("/docs/errors/access-denied"));
        problemDetail.setProperty(TIMESTAMP, Instant.now().toEpochMilli());
        return problemDetail;
    }

    @ExceptionHandler({ BadRequestException.class, ConstraintViolationException.class})
    public ProblemDetail handleBadRequest(Exception ex) {
        log.error("Bad request error: {}", ex.getMessage(), ex);
        return getBadRequestProblemDetail(ex);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.error("Method argument not valid error: {}", ex.getMessage(), ex);
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Bad Request");
        problemDetail.setType(URI.create("/docs/errors/bad-request"));
        problemDetail.setProperty(TIMESTAMP, Instant.now().toEpochMilli());
        StringBuilder message = new StringBuilder();
        for (FieldError error : ex.getFieldErrors()) {
            message.append("field '").append(error.getField()).append("' ").append(error.getDefaultMessage()).append(";");
        }
        problemDetail.setProperty(DESCRIPTION, message.toString());
        return handleExceptionInternal(ex, problemDetail, headers, status, request);
    }

    private ProblemDetail getBadRequestProblemDetail(Exception ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Bad Request");
        problemDetail.setType(URI.create("/docs/errors/bad-request"));
        problemDetail.setProperty(TIMESTAMP, Instant.now().toEpochMilli());
        problemDetail.setProperty(DESCRIPTION, ex.getMessage());
        return  problemDetail;
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                            HttpHeaders headers, HttpStatusCode status,
                                                                            WebRequest request) {
        log.error("Not readable body error: {}", ex.getMessage(), ex);
        ProblemDetail problemDetail = createProblemDetail(ex, status, "Failed to read request",
                null, null, request);
        problemDetail.setProperty(TIMESTAMP, Instant.now().toEpochMilli());
        problemDetail.setProperty(DESCRIPTION, "The request body was expected but is missing. Please check the request body.");
        return handleExceptionInternal(ex, problemDetail, headers, status, request);
    }
}
