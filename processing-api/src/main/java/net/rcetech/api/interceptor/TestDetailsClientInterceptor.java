package net.rcetech.api.interceptor;

import io.grpc.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * gRPC ClientInterceptor для проброса заголовка Test-Details в запросах на получение реквизитов мерчанта.
 */
@Component
@Slf4j
public class TestDetailsClientInterceptor implements ClientInterceptor {

    public static final Metadata.Key<String> TEST_DETAILS_HEADER_KEY =
            Metadata.Key.of("Test-Details", Metadata.ASCII_STRING_MARSHALLER);

    public static final Context.Key<String> TEST_DETAILS_CTX_KEY = Context.key("testDetails");

    public static void runWithTestDetails(Runnable runnable) {
        runWithTestDetails("true", runnable);
    }

    public static void runWithTestDetails(String testDetails, Runnable runnable) {
        Context.current().withValue(TEST_DETAILS_CTX_KEY, testDetails).run(runnable);
    }

    @Override
    public <Q, R> ClientCall<Q, R> interceptCall(
            MethodDescriptor<Q, R> method,
            CallOptions callOptions,
            Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<R> responseListener, Metadata headers) {
                String testDetails = TEST_DETAILS_CTX_KEY.get();
                if (testDetails == null) {
                    testDetails = getFromHttpRequest();
                }

                if ("true".equalsIgnoreCase(testDetails) && !headers.containsKey(TEST_DETAILS_HEADER_KEY)) {
                    log.debug("Добавление gRPC заголовка {}: true", TEST_DETAILS_HEADER_KEY.name());
                    headers.put(TEST_DETAILS_HEADER_KEY, "true");
                }

                super.start(responseListener, headers);
            }
        };
    }

    private String getFromHttpRequest() {
        try {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
                HttpServletRequest request = servletRequestAttributes.getRequest();
                return request.getHeader(TEST_DETAILS_HEADER_KEY.name());
            }
        } catch (Exception e) {
            log.trace("Не удалось получить Test-Details из HTTP запроса: {}", e.getMessage());
        }
        return null;
    }

}
