package net.rcetech.api.service.unit;

import com.google.rpc.BadRequest;
import com.google.rpc.Status;
import io.grpc.protobuf.StatusProto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import net.rcetech.api.controller.handler.GlobalExceptionHandler;
import net.rcetech.api.exceptions.BaseException;
import net.rcetech.api.exceptions.OrderNotFoundException;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler Unit Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void shouldHandleOrderNotFoundException() {
        var orderId = "order-123";
        var exception = new OrderNotFoundException(orderId);

        var result = handler.handleOrderNotFound(exception);

        assertThat(result)
                .satisfies(pd -> {
                    assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
                    assertThat(pd.getTitle()).isEqualTo("Order not found.");
                    assertThat(pd.getDetail()).isEqualTo("Order with id order-123 not found.");
                    assertThat(pd.getType()).isEqualTo(URI.create("/errors/order-not-found"));
                    assertThat(pd.getProperties()).containsKey("timestamp");
                });
    }

    @Test
    void shouldHandleMerchantDetailsNotFoundException() {
        var result = handler.handleMerchantDetailsNotFound();

        assertThat(result)
                .satisfies(pd -> {
                    assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
                    assertThat(pd.getTitle()).isEqualTo("Details were not found.");
                    assertThat(pd.getDetail()).isEqualTo("Details were not found. Try again later.");
                    assertThat(pd.getType()).isEqualTo(URI.create("/errors/details-not-found"));
                    assertThat(pd.getProperties()).containsKey("timestamp");
                });
    }

    @Test
    void shouldHandleEnableUniqueAmountException() {
        var result = handler.handleEnableUniqueAmountError();

        assertThat(result)
                .satisfies(pd -> {
                    assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
                    assertThat(pd.getTitle())
                            .isEqualTo("Something went wrong. Please report this issue to our support team.");
                    assertThat(pd.getType()).isEqualTo(URI.create("/errors/internal-server-error"));
                    assertThat(pd.getProperties()).containsKey("timestamp");
                });
    }

    @Test
    void shouldHandleMethodArgumentNotValidException() {
        var fieldError1 = new FieldError("object", "username", "Username cannot be empty");
        var fieldError2 = new FieldError("object", "email", "Email must be valid");

        var bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));

        var exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);

        var result = handler.handleValidationErrors(exception);

        assertThat(result)
                .satisfies(pd -> {
                    assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
                    assertThat(pd.getTitle()).isEqualTo("Invalid request.");
                    assertThat(pd.getDetail())
                            .isEqualTo("username: Username cannot be empty, email: Email must be valid");
                    assertThat(pd.getType()).isEqualTo(URI.create("/errors/now-valid"));
                    assertThat(pd.getProperties()).containsKey("timestamp");
                });
    }

    @Test
    void shouldHandleMethodArgumentNotValidExceptionWithNoErrors() {
        var bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of());

        var exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);

        var result = handler.handleValidationErrors(exception);

        assertThat(result)
                .satisfies(pd -> {
                    assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
                    assertThat(pd.getDetail()).isEmpty();
                });
    }

    @Test
    void shouldHandleCompletionExceptionWithNotFound() {
        var grpcException = io.grpc.Status.NOT_FOUND
                .withDescription("Client not found")
                .asRuntimeException();
        var completionException = new CompletionException(grpcException);

        var result = handler.handleGrpcException(completionException);

        assertThat(result)
                .satisfies(pd -> {
                    assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
                    assertThat(pd.getDetail()).isEqualTo("Client not found");
                    assertThat(pd.getTitle()).isEqualTo("NOT_FOUND");
                    assertThat(pd.getType()).isEqualTo(URI.create("/errors/not_found"));
                    assertThat(pd.getProperties()).containsKey("timestamp");
                });
    }

    @Test
    void shouldHandleCompletionExceptionWithInvalidArgument() {
        var grpcException = io.grpc.Status.INVALID_ARGUMENT
                .withDescription("Invalid key format")
                .asRuntimeException();
        var completionException = new CompletionException(grpcException);

        var result = handler.handleGrpcException(completionException);

        assertThat(result)
                .satisfies(pd -> {
                    assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
                    assertThat(pd.getDetail()).isEqualTo("Invalid key format");
                    assertThat(pd.getTitle()).isEqualTo("INVALID_ARGUMENT");
                    assertThat(pd.getType()).isEqualTo(URI.create("/errors/invalid_argument"));
                    assertThat(pd.getProperties()).containsKey("timestamp");
                });
    }

    @Test
    void shouldHandleCompletionExceptionWithPermissionDenied() {
        var grpcException = io.grpc.Status.PERMISSION_DENIED
                .withDescription("Access denied")
                .asRuntimeException();
        var completionException = new CompletionException(grpcException);

        var result = handler.handleGrpcException(completionException);

        assertThat(result)
                .satisfies(pd -> {
                    assertThat(pd.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
                    assertThat(pd.getDetail()).isEqualTo("Access denied");
                    assertThat(pd.getTitle()).isEqualTo("PERMISSION_DENIED");
                    assertThat(pd.getType()).isEqualTo(URI.create("/errors/permission_denied"));
                    assertThat(pd.getProperties()).containsKey("timestamp");
                });
    }

    @Test
    void shouldHandleCompletionExceptionWithUnauthenticated() {
        var grpcException = io.grpc.Status.UNAUTHENTICATED
                .withDescription("Unauthenticated")
                .asRuntimeException();
        var completionException = new CompletionException(grpcException);

        var result = handler.handleGrpcException(completionException);

        assertThat(result)
                .satisfies(pd -> {
                    assertThat(pd.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
                    assertThat(pd.getDetail()).isEqualTo("Unauthenticated");
                    assertThat(pd.getTitle()).isEqualTo("UNAUTHENTICATED");
                    assertThat(pd.getType()).isEqualTo(URI.create("/errors/unauthenticated"));
                    assertThat(pd.getProperties()).containsKey("timestamp");
                });
    }

    @Test
    void shouldReturn503ForUnavailable() {
        var grpcException = io.grpc.Status.UNAVAILABLE
                .withDescription("Service unavailable")
                .asRuntimeException();
        var completionException = new CompletionException(grpcException);

        var result = handler.handleGrpcException(completionException);

        assertThat(result)
                .satisfies(pd -> {
                    assertThat(pd.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
                    assertThat(pd.getTitle()).isEqualTo("Service Unavailable");
                    assertThat(pd.getDetail())
                            .isEqualTo("Something went wrong. Please report this issue to our support team.");
                    assertThat(pd.getType()).isEqualTo(URI.create("/errors/service-unavailable"));
                    assertThat(pd.getProperties()).containsKey("timestamp");
                });
    }

    @Test
    void shouldReturn503ForInternal() {
        var grpcException = io.grpc.Status.INTERNAL
                .withDescription("Internal error")
                .asRuntimeException();
        var completionException = new CompletionException(grpcException);

        var result = handler.handleGrpcException(completionException);

        assertThat(result)
                .satisfies(pd -> {
                    assertThat(pd.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
                    assertThat(pd.getTitle()).isEqualTo("Service Unavailable");
                    assertThat(pd.getDetail())
                            .isEqualTo("Something went wrong. Please report this issue to our support team.");
                    assertThat(pd.getType()).isEqualTo(URI.create("/errors/service-unavailable"));
                    assertThat(pd.getProperties()).containsKey("timestamp");
                });
    }

    @Test
    void shouldHandleStatusRuntimeExceptionWithBadRequestDetails() {
        var badRequest = BadRequest.newBuilder()
                .addFieldViolations(
                        BadRequest.FieldViolation.newBuilder()
                                .setField("amount")
                                .setDescription("Amount must be positive")
                                .build()
                )
                .addFieldViolations(
                        BadRequest.FieldViolation.newBuilder()
                                .setField("currency")
                                .setDescription("Currency must be USD or EUR")
                                .build()
                )
                .build();

        var status = Status.newBuilder()
                .setMessage("Validation failed")
                .addDetails(com.google.protobuf.Any.pack(badRequest))
                .build();

        var grpcException = StatusProto.toStatusRuntimeException(status);
        var completionException = new CompletionException(grpcException);

        var result = handler.handleGrpcException(completionException);

        assertThat(result)
                .satisfies(pd -> {
                    assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
                    assertThat(pd.getTitle()).isEqualTo("Validation failed");
                    assertThat(pd.getDetail()).isEqualTo("Validation failed");
                    assertThat(pd.getType()).isEqualTo(URI.create("/errors/validation-error"));
                    assertThat(pd.getProperties())
                            .containsKey("invalid_params")
                            .containsKey("timestamp");

                    @SuppressWarnings("unchecked")
                    var errors = Optional.ofNullable(pd.getProperties())
                            .map(props -> (java.util.Map<String, String>) props.get("invalid_params"))
                            .orElseGet(Collections::emptyMap);
                    assertThat(errors)
                            .containsEntry("amount", "Amount must be positive")
                            .containsEntry("currency", "Currency must be USD or EUR");
                });
    }

    @Test
    void shouldHandleStatusRuntimeExceptionWithoutDetails() {
        var grpcException = io.grpc.Status.NOT_FOUND
                .withDescription("")
                .asRuntimeException();
        var completionException = new CompletionException(grpcException);

        var result = handler.handleGrpcException(completionException);

        assertThat(result)
                .satisfies(pd -> {
                    assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
                    assertThat(pd.getDetail()).isEqualTo("Not Found");
                    assertThat(pd.getTitle()).isEqualTo("NOT_FOUND");
                });
    }

    @Test
    void shouldHandlePlainStatusRuntimeException() {
        var grpcException = io.grpc.Status.NOT_FOUND
                .withDescription("Client not found")
                .asRuntimeException();

        var result = handler.handleGrpcException(grpcException);

        assertThat(result)
                .satisfies(pd -> {
                    assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
                    assertThat(pd.getDetail()).isEqualTo("Client not found");
                    assertThat(pd.getTitle()).isEqualTo("NOT_FOUND");
                });
    }

    @Test
    void shouldHandleCompletionExceptionWithNonGrpcCause() {
        var cause = new RuntimeException("Unexpected error");
        var completionException = new CompletionException(cause);

        var result = handler.handleGrpcException(completionException);

        assertThat(result)
                .satisfies(pd -> {
                    assertThat(pd.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
                    assertThat(pd.getTitle()).isEqualTo("Service Unavailable");
                    assertThat(pd.getDetail())
                            .isEqualTo("Something went wrong. Please report this issue to our support team.");
                    assertThat(pd.getType()).isEqualTo(URI.create("/errors/service-unavailable"));
                    assertThat(pd.getProperties()).containsKey("timestamp");
                });
    }

    @Test
    @DisplayName("Should handle BaseException")
    void shouldHandleBaseException() {
        var exception = new BaseException("Something went wrong");

        var result = handler.handleUnexpectedErrors(exception);

        assertThat(result)
                .satisfies(pd -> {
                    assertThat(pd.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
                    assertThat(pd.getTitle()).isEqualTo("Service Unavailable");
                    assertThat(pd.getDetail()).isNull();
                    assertThat(pd.getType()).isEqualTo(URI.create("/errors/internal-server-error"));
                    assertThat(pd.getProperties()).containsKey("timestamp");
                });
    }

    @Test
    void shouldHandleGenericException() {
        var exception = new RuntimeException("Unexpected system error");

        var result = handler.handleUnexpectedErrors(exception);

        assertThat(result)
                .satisfies(pd -> {
                    assertThat(pd.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
                    assertThat(pd.getTitle()).isEqualTo("Service Unavailable");
                    assertThat(pd.getDetail()).isNull();
                    assertThat(pd.getType()).isEqualTo(URI.create("/errors/internal-server-error"));
                    assertThat(pd.getProperties())
                            .containsKey("timestamp")
                            .hasSize(1);
                });
    }

    @Test
    void shouldMapGrpcCodesToHttpStatuses() {
        var grpcException = io.grpc.Status.NOT_FOUND.asRuntimeException();
        var completionException = new CompletionException(grpcException);

        var result = handler.handleGrpcException(completionException);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());

        var invalidArgException = io.grpc.Status.INVALID_ARGUMENT.asRuntimeException();
        var invalidResult = handler.handleGrpcException(new CompletionException(invalidArgException));
        assertThat(invalidResult.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());

        var permissionDeniedException = io.grpc.Status.PERMISSION_DENIED.asRuntimeException();
        var permissionResult = handler.handleGrpcException(new CompletionException(permissionDeniedException));
        assertThat(permissionResult.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());

        var unauthenticatedException = io.grpc.Status.UNAUTHENTICATED.asRuntimeException();
        var unauthenticatedResult = handler.handleGrpcException(new CompletionException(unauthenticatedException));
        assertThat(unauthenticatedResult.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

}
