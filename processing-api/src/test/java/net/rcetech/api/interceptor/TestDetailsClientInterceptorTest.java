package net.rcetech.api.interceptor;

import io.grpc.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TestDetailsClientInterceptorTest {

    private final TestDetailsClientInterceptor interceptor = new TestDetailsClientInterceptor();

    private final MethodDescriptor<Void, Void> methodDescriptor = MethodDescriptor.<Void, Void>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("test.Service/TestMethod")
            .setRequestMarshaller(new NoopMarshaller())
            .setResponseMarshaller(new NoopMarshaller())
            .build();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("Заголовок Test-Details должен добавляться, если установлен контекст gRPC TEST_DETAILS_CTX_KEY=true.")
    void interceptCall_shouldAddHeaderWhenContextIsTrue() {
        Channel channel = mock(Channel.class);
        @SuppressWarnings("unchecked")
        ClientCall<Void, Void> rawCall = mock(ClientCall.class);
        doReturn(rawCall).when(channel).newCall(any(), any());

        Metadata headers = new Metadata();

        TestDetailsClientInterceptor.runWithTestDetails(() -> {
            ClientCall<Void, Void> interceptedCall = interceptor.interceptCall(methodDescriptor, CallOptions.DEFAULT,
                    channel);
            interceptedCall.start(mock(ClientCall.Listener.class), headers);
        });

        assertEquals("true", headers.get(TestDetailsClientInterceptor.TEST_DETAILS_HEADER_KEY));
        verify(rawCall).start(any(), eq(headers));
    }

    @Test
    @DisplayName("Заголовок Test-Details должен добавляться, если HTTP запрос содержит Test-Details: true.")
    void interceptCall_shouldAddHeaderWhenHttpRequestHasHeader() {
        Channel channel = mock(Channel.class);
        @SuppressWarnings("unchecked")
        ClientCall<Void, Void> rawCall = mock(ClientCall.class);
        doReturn(rawCall).when(channel).newCall(any(), any());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Test-Details", "true");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Metadata headers = new Metadata();
        ClientCall<Void, Void> interceptedCall = interceptor.interceptCall(methodDescriptor, CallOptions.DEFAULT,
                channel);
        interceptedCall.start(mock(ClientCall.Listener.class), headers);

        assertEquals("true", headers.get(TestDetailsClientInterceptor.TEST_DETAILS_HEADER_KEY));
        verify(rawCall).start(any(), eq(headers));
    }

    @Test
    @DisplayName("Заголовок Test-Details НЕ должен добавляться, если контекст пуст и HTTP запрос без заголовка.")
    void interceptCall_shouldNotAddHeaderWhenNoContextAndNoHttpRequest() {
        Channel channel = mock(Channel.class);
        @SuppressWarnings("unchecked")
        ClientCall<Void, Void> rawCall = mock(ClientCall.class);
        doReturn(rawCall).when(channel).newCall(any(), any());

        Metadata headers = new Metadata();
        ClientCall<Void, Void> interceptedCall = interceptor.interceptCall(methodDescriptor, CallOptions.DEFAULT,
                channel);
        interceptedCall.start(mock(ClientCall.Listener.class), headers);

        assertNull(headers.get(TestDetailsClientInterceptor.TEST_DETAILS_HEADER_KEY));
        verify(rawCall).start(any(), eq(headers));
    }

    @ParameterizedTest
    @ValueSource(strings = { "false", "0", "no", "random" })
    @DisplayName("Заголовок Test-Details НЕ должен добавляться, если контекст не равен true.")
    void interceptCall_shouldNotAddHeaderWhenContextIsNotTrue(String value) {
        Channel channel = mock(Channel.class);
        @SuppressWarnings("unchecked")
        ClientCall<Void, Void> rawCall = mock(ClientCall.class);
        doReturn(rawCall).when(channel).newCall(any(), any());

        Metadata headers = new Metadata();

        TestDetailsClientInterceptor.runWithTestDetails(value, () -> {
            ClientCall<Void, Void> interceptedCall = interceptor.interceptCall(methodDescriptor, CallOptions.DEFAULT,
                    channel);
            interceptedCall.start(mock(ClientCall.Listener.class), headers);
        });

        assertNull(headers.get(TestDetailsClientInterceptor.TEST_DETAILS_HEADER_KEY));
        verify(rawCall).start(any(), eq(headers));
    }

    @Test
    @DisplayName("Заголовок Test-Details не должен дублироваться, если он уже присутствует в Metadata.")
    void interceptCall_shouldNotDuplicateHeaderIfAlreadyPresent() {
        Channel channel = mock(Channel.class);
        @SuppressWarnings("unchecked")
        ClientCall<Void, Void> rawCall = mock(ClientCall.class);
        doReturn(rawCall).when(channel).newCall(any(), any());

        Metadata headers = new Metadata();
        headers.put(TestDetailsClientInterceptor.TEST_DETAILS_HEADER_KEY, "true");

        TestDetailsClientInterceptor.runWithTestDetails(() -> {
            ClientCall<Void, Void> interceptedCall = interceptor.interceptCall(methodDescriptor, CallOptions.DEFAULT,
                    channel);
            interceptedCall.start(mock(ClientCall.Listener.class), headers);
        });

        Iterable<String> values = headers.getAll(TestDetailsClientInterceptor.TEST_DETAILS_HEADER_KEY);
        assertNotNull(values);
        int count = 0;
        for (String ignored : values) {
            count++;
        }
        assertEquals(1, count);
    }

    @Test
    @DisplayName("runWithTestDetails должен устанавливать контекст при выполнении Runnable.")
    void runWithTestDetails_shouldSetContext() {
        AtomicBoolean wasExecuted = new AtomicBoolean(false);
        TestDetailsClientInterceptor.runWithTestDetails(() -> {
            assertEquals("true", TestDetailsClientInterceptor.TEST_DETAILS_CTX_KEY.get());
            wasExecuted.set(true);
        });
        assertTrue(wasExecuted.get());
        assertNull(TestDetailsClientInterceptor.TEST_DETAILS_CTX_KEY.get());
    }

    private static class NoopMarshaller implements MethodDescriptor.Marshaller<Void> {

        @Override
        public InputStream stream(Void value) {
            return InputStream.nullInputStream();
        }

        @Override
        public Void parse(InputStream stream) {
            return null;
        }

    }

}
