package net.rcetech.meta;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import net.rcetech.meta.exception.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.databind.exc.InvalidFormatException;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@ControllerAdvice
@NullMarked
@Slf4j
public class GlobalControllerAdvice extends ResponseEntityExceptionHandler {

    private static final String TIMESTAMP = "timestamp";
    private static final String DESCRIPTION = "description";
    private static final String CONTACT_SUPPORT = "Contact support.";
    private static final String NOT_HANDLED_ERROR_LOG = "{} Controller not handled error: {}";
    private static final String INTERNAL_SERVER_ERROR_TITLE = "Internal Server Error";
    private static final String INTERNAL_SERVER_ERROR_TYPE = "/errors/internal-server-error";
    private static final String BAD_REQUEST_TITLE = "Bad Request";
    private static final String BAD_REQUEST_TYPE = "/docs/errors/bad-request";
    private static final Map<Pattern, String> BAD_REQUEST_CONSTRAINT_MESSAGES = Map.of(
            constraintPattern(DatabaseConstraint.UNIQUE_INTERNAL_ID), ExceptionMessage.INTERNAL_ID_NOT_UNIQUE
    );

    private static Pattern constraintPattern(String constraintName) {
        return Pattern.compile("(^|[\\s'\"`.\\[])" + Pattern.quote(constraintName) + "($|[\\s'\"`\\]])");
    }

    @ExceptionHandler({ ServiceException.class })
    public ProblemDetail handle(ServiceException ex) {
        long epochMilli = Instant.now().toEpochMilli();
        log.error("{} Controller business error: {}", epochMilli, ex.getMessage(), ex);
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problemDetail.setTitle(ex.getMessage());
        problemDetail.setType(URI.create(INTERNAL_SERVER_ERROR_TYPE));
        problemDetail.setProperty(TIMESTAMP, epochMilli);
        problemDetail.setProperty(DESCRIPTION, CONTACT_SUPPORT);
        return problemDetail;
    }

    @ExceptionHandler({ BaseException.class })
    public ProblemDetail handleGenericException(BaseException ex) {
        log.error(NOT_HANDLED_ERROR_LOG, ex.getTimestamp(), ex.getMessage(), ex);
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problemDetail.setTitle(INTERNAL_SERVER_ERROR_TITLE);
        problemDetail.setType(URI.create(INTERNAL_SERVER_ERROR_TYPE));
        problemDetail.setProperty(TIMESTAMP, ex.getTimestamp());
        problemDetail.setProperty(DESCRIPTION, CONTACT_SUPPORT);
        return problemDetail;
    }

    @ExceptionHandler({ Exception.class })
    public ProblemDetail handleGenericException(Exception ex) {
        long epochMilli = Instant.now().toEpochMilli();
        log.error(NOT_HANDLED_ERROR_LOG, epochMilli, ex.getMessage(), ex);
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problemDetail.setTitle(INTERNAL_SERVER_ERROR_TITLE);
        problemDetail.setType(URI.create(INTERNAL_SERVER_ERROR_TYPE));
        problemDetail.setProperty(TIMESTAMP, epochMilli);
        problemDetail.setProperty(DESCRIPTION, CONTACT_SUPPORT);
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

    @ExceptionHandler({ DataIntegrityViolationException.class })
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String causeMessage = Objects.requireNonNullElse(NestedExceptionUtils.getMostSpecificCause(ex).getMessage(), "");
        return BAD_REQUEST_CONSTRAINT_MESSAGES.entrySet().stream()
                .filter(constraint -> constraint.getKey().matcher(causeMessage).find())
                .findFirst()
                .map(constraint -> {
                    log.error("Bad request constraint violation error: {}", ex.getMessage(), ex);
                    ProblemDetail problemDetail = getBadRequestProblemDetail(ex);
                    problemDetail.setProperty(DESCRIPTION, constraint.getValue());
                    return problemDetail;
                })
                .orElseGet(() -> handleGenericException(ex));
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.error("Method argument not valid error: {}", ex.getMessage(), ex);
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle(BAD_REQUEST_TITLE);
        problemDetail.setType(URI.create(BAD_REQUEST_TYPE));
        problemDetail.setProperty(TIMESTAMP, Instant.now().toEpochMilli());
        StringBuilder message = new StringBuilder();
        appendFieldErrors(message, ex.getFieldErrors());
        problemDetail.setProperty(DESCRIPTION, message.toString());
        return handleExceptionInternal(ex, problemDetail, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.error("Handler method validation error: {}", ex.getMessage(), ex);
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle(BAD_REQUEST_TITLE);
        problemDetail.setType(URI.create(BAD_REQUEST_TYPE));
        problemDetail.setProperty(TIMESTAMP, Instant.now().toEpochMilli());
        StringBuilder message = new StringBuilder();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors errors) {
                appendFieldErrors(message, errors.getFieldErrors());
            } else {
                result.getResolvableErrors().forEach(error -> message.append(error.getDefaultMessage()));
            }
        }
        problemDetail.setProperty(DESCRIPTION, message.toString());
        return handleExceptionInternal(ex, problemDetail, headers, HttpStatus.BAD_REQUEST, request);
    }

    private void appendFieldErrors(StringBuilder message, List<FieldError> fieldErrors) {
        for (FieldError error : fieldErrors) {
            message.append("field '").append(error.getField()).append("' ").append(error.getDefaultMessage()).append(";");
        }
    }

    private ProblemDetail getBadRequestProblemDetail(Exception ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle(BAD_REQUEST_TITLE);
        problemDetail.setType(URI.create(BAD_REQUEST_TYPE));
        problemDetail.setProperty(TIMESTAMP, Instant.now().toEpochMilli());
        problemDetail.setProperty(DESCRIPTION, ex.getMessage());
        return problemDetail;
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                            HttpHeaders headers, HttpStatusCode status,
                                                                            WebRequest request) {
        log.error("Not readable body error: {}", ex.getMessage(), ex);
        ProblemDetail problemDetail = createProblemDetail(ex, status, "Failed to read request",
                null, null, request);
        problemDetail.setProperty(TIMESTAMP, Instant.now().toEpochMilli());
        if (ex.getCause() instanceof InvalidFormatException invalidFormatException) {
            return new ResponseEntity<>(handle(invalidFormatException), HttpStatus.BAD_REQUEST);
        }
        if (Objects.nonNull(ex.getMessage()) && ex.getMessage().startsWith("Required request body is missing")) {
            problemDetail.setProperty(DESCRIPTION, "Required body is missing.");
        } else if (ex.getCause() instanceof InvalidFormatException) {
            problemDetail.setProperty(DESCRIPTION, ex.getCause().getMessage());
        } else {
            problemDetail.setProperty(DESCRIPTION, "The request body is missing or contains invalid data.");
        }
        return handleExceptionInternal(ex, problemDetail, headers, status, request);
    }

    public ProblemDetail handle(InvalidFormatException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle(BAD_REQUEST_TITLE);
        problemDetail.setType(URI.create(BAD_REQUEST_TYPE));
        problemDetail.setProperty(TIMESTAMP, Instant.now().toEpochMilli());
        problemDetail.setProperty(DESCRIPTION, "Unknown value '" + ex.getValue() + "'.");
        return problemDetail;
    }

    @ExceptionHandler({ ServiceUnavailableException.class })
    public ProblemDetail handle(ServiceUnavailableException ex) {
        long epochMilli = Instant.now().toEpochMilli();
        log.error("{} Server error: {}", epochMilli, ex.getMessage(), ex);
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problemDetail.setTitle("Service Unavailable");
        problemDetail.setType(URI.create("/docs/errors/service-unavailable"));
        problemDetail.setProperty(TIMESTAMP, epochMilli);
        problemDetail.setProperty(DESCRIPTION, CONTACT_SUPPORT);
        return problemDetail;
    }
}
