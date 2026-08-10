package net.rcetech.clientsapi.controller.handler;

import build.buf.protovalidate.ValidationResult;
import build.buf.protovalidate.Validator;
import build.buf.protovalidate.ValidatorFactory;
import build.buf.protovalidate.exceptions.ValidationException;
import build.buf.validate.FieldPathElement;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import com.google.rpc.BadRequest;
import com.google.rpc.Code;
import io.grpc.*;
import io.grpc.protobuf.StatusProto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@GlobalServerInterceptor
public class GrpcValidationInterceptor implements ServerInterceptor {

    private final Validator validator = ValidatorFactory.newBuilder().build();

    @Override
    public <R, T> ServerCall.Listener<R> interceptCall(
            ServerCall<R, T> call, Metadata headers, ServerCallHandler<R, T> next) {

        ServerCall.Listener<R> delegate = next.startCall(call, headers);

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {

            @Override
            public void onMessage(R message) {
                if (message instanceof Message protobufMessage) {
                    try {
                        ValidationResult result = validator.validate(protobufMessage);
                        if (!result.isSuccess()) {
                            com.google.rpc.Status rpcStatus = buildRpcStatus(result);
                            StatusRuntimeException statusEx = StatusProto.toStatusRuntimeException(rpcStatus);

                            call.close(statusEx.getStatus(), statusEx.getTrailers());
                            return;
                        }
                    } catch (ValidationException e) {
                        log.error("Protovalidate internal error: ", e);
                        StatusRuntimeException internalEx = Status.INTERNAL
                                .withDescription("Внутренняя ошибка проверки контракта")
                                .asRuntimeException();
                        call.close(internalEx.getStatus(), internalEx.getTrailers());
                        return;
                    }
                }
                super.onMessage(message);
            }
        };
    }

    private com.google.rpc.Status buildRpcStatus(ValidationResult result) {
        BadRequest.Builder badRequestBuilder = BadRequest.newBuilder();
        for (var violation : result.toProto().getViolationsList()) {
            String fieldPathStr = violation.getField().getElementsList().stream()
                    .map(FieldPathElement::getFieldName)
                    .collect(java.util.stream.Collectors.joining("."));
            badRequestBuilder.addFieldViolations(
                    BadRequest.FieldViolation.newBuilder()
                            .setField(fieldPathStr)
                            .setDescription(violation.getMessage())
                            .build()
            );
        }

        return com.google.rpc.Status.newBuilder()
                .setCode(Code.INVALID_ARGUMENT.getNumber())
                .setMessage("Ошибка валидации входных параметров")
                .addDetails(Any.pack(badRequestBuilder.build()))
                .build();
    }

}
