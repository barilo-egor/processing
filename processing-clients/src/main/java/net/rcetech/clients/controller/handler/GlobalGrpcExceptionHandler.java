package net.rcetech.clients.controller.handler;

import com.google.rpc.Code;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.protobuf.StatusProto;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;
import org.springframework.stereotype.Component;
import net.rcetech.clients.exceptions.CustomException;

@Slf4j
@Component
public class GlobalGrpcExceptionHandler implements GrpcExceptionHandler {

    @Override
    public io.grpc.StatusException handleException(@NonNull Throwable ex) {
        if (ex instanceof CustomException customEx) {
            Code grpcCode = customEx.getErrorCode();
            return buildStatusException(grpcCode, ex.getMessage(), customEx.getField(), customEx.getDescription());
        }
        log.error("Unexpected system error: ", ex);
        return Status.INTERNAL
                .withDescription("Internal server error")
                .asException();
    }

    private StatusException buildStatusException(com.google.rpc.Code code, String message, String field,
            String description) {
        com.google.rpc.Status.Builder statusBuilder = com.google.rpc.Status.newBuilder()
                .setCode(code.getNumber())
                .setMessage(message != null ? message : "");

        com.google.rpc.BadRequest badRequest = com.google.rpc.BadRequest.newBuilder()
                .addFieldViolations(com.google.rpc.BadRequest.FieldViolation.newBuilder()
                        .setField(field != null ? field : "")
                        .setDescription(description != null ? description : "")
                        .build())
                .build();
        statusBuilder.addDetails(com.google.protobuf.Any.pack(badRequest));

        var runtimeEx = StatusProto.toStatusRuntimeException(statusBuilder.build());
        return new StatusException(runtimeEx.getStatus(), runtimeEx.getTrailers());
    }

}
