package net.rcetech.meta;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.rcetech.meta.exception.BadRequestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalControllerAdviceTest {

    private final GlobalControllerAdvice advice = new GlobalControllerAdvice();

    private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalControllerAdvice.class);

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private Level previousLevel;

    @BeforeEach
    void setUp() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.TRACE);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Заявка не найдена.", "IP-адрес 10.0.0.1 уже добавлен."})
    @DisplayName("BadRequestException должен логироваться уровнем TRACE.")
    void handleBadRequest_shouldLogTrace(String message) {
        advice.handleBadRequest(new BadRequestException(message));

        assertLevels(List.of(Level.TRACE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Connection refused", "Unexpected state"})
    @DisplayName("Необработанное исключение должно логироваться уровнем ERROR.")
    void handleGenericException_shouldLogError(String message) {
        advice.handleGenericException(new IllegalStateException(message));

        assertLevels(List.of(Level.ERROR));
    }

    @ParameterizedTest
    @MethodSource("clientErrors")
    @DisplayName("Стандартная ошибка Spring со статусом 4xx должна логироваться уровнем TRACE.")
    void handleException_shouldLogTraceForClientError(Exception ex) throws Exception {
        advice.handleException(ex, webRequest());

        assertLevels(List.of(Level.TRACE));
    }

    @ParameterizedTest
    @MethodSource("serverErrors")
    @DisplayName("Стандартная ошибка Spring со статусом 5xx должна логироваться уровнем ERROR.")
    void handleException_shouldLogErrorForServerError(Exception ex) throws Exception {
        advice.handleException(ex, webRequest());

        assertLevels(List.of(Level.ERROR));
    }

    private static Stream<Exception> clientErrors() {
        return Stream.of(
                new MissingServletRequestParameterException("name", "String"),
                new HttpRequestMethodNotSupportedException("PUT")
        );
    }

    private static Stream<Exception> serverErrors() {
        return Stream.of(
                new AsyncRequestTimeoutException(),
                new HttpMessageNotWritableException("Could not write JSON")
        );
    }

    private static ServletWebRequest webRequest() {
        return new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse());
    }

    private void assertLevels(List<Level> expected) {
        assertEquals(expected, appender.list.stream().map(ILoggingEvent::getLevel).toList());
    }
}
