package net.rcetech.api.controller.v1;

import net.rcetech.api.config.ApiSecurityConfig;
import net.rcetech.api.controller.ApiOrderController;
import net.rcetech.api.dto.CreateOrderCommand;
import net.rcetech.api.dto.CreateOrderRequest;
import net.rcetech.api.service.LimitedOrderCreator;
import net.rcetech.api.service.OrderApiService;
import net.rcetech.domain.model.clients.ApiKey;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.domain.service.clients.ApiKeyService;
import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.exception.ExceptionMessage;
import net.rcetech.meta.exception.ServiceUnavailableException;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.RequestMethod;
import net.rcetech.meta.orders.dto.ClientOrderFilter;
import net.rcetech.meta.orders.dto.ClientOrderSummary;
import net.rcetech.meta.user.Role;
import org.hamcrest.CustomMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiOrderController.class)
@Import({MetaSecurityConfig.class, ApiSecurityConfig.class, LimitedOrderCreator.class})
class ApiOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderApiService orderApiService;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private ApiKeyService apiKeyService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(orderApiService.createOrder(any())).thenReturn(summary());
        when(orderApiService.createOrderAsync(any(), any())).thenReturn(summary());
    }

    private static ClientOrderSummary summary() {
        return new ClientOrderSummary(UUID.randomUUID(), Instant.now(), UUID.randomUUID().toString(), OrderStatus.NEW,
                1000, RequestMethod.CARD, "1234 1234 1234 1234", "Т-Банк", null);
    }

    private RequestPostProcessor apiKeyOf(UUID clientId) {
        String headerApiKey = "key-" + clientId;
        Client client = new Client();
        client.setId(clientId);
        ApiKey apiKey = new ApiKey();
        apiKey.setClient(client);
        when(apiKeyService.findByKey(headerApiKey)).thenReturn(Optional.of(apiKey));
        return request -> {
            request.addHeader("Api-Key", headerApiKey);
            return request;
        };
    }

    @Test
    @DisplayName("Метод должен вернуть 400 если отсутствует тело.")
    void createOrder_shouldReturn400IfNoBody() throws Exception {
        mockMvc.perform(post("/api/v1/order")
                        .with(apiKeyOf(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").doesNotExist())
                .andExpect(jsonPath("$.instance").value("/api/v1/order"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNumber())
                .andExpect(jsonPath("$.description")
                        .value("Тело запроса не передано."));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Duplicate entry 'order-1' for key 'orders.unique_internal_id'|400|" + ExceptionMessage.INTERNAL_ID_NOT_UNIQUE,
            "Duplicate entry 'order-2' for key unique_internal_id|400|" + ExceptionMessage.INTERNAL_ID_NOT_UNIQUE,
            "unique_internal_id|400|" + ExceptionMessage.INTERNAL_ID_NOT_UNIQUE,
            "Duplicate entry 'order-1' for key 'orders.unique_internal_id_v2'|500|Обратитесь в поддержку.",
            "Duplicate entry 'order-1' for key 'orders.not_unique_internal_id'|500|Обратитесь в поддержку.",
            "Duplicate entry 'ALFA_TEAM-1' for key 'orders.unique_merchant_order_id'|500|Обратитесь в поддержку.",
            "Column 'amount' cannot be null|500|Обратитесь в поддержку."
    })
    @DisplayName("Метод должен вернуть 400 при нарушении известного ограничения БД и 500 при нарушении остальных.")
    void createOrder_shouldMapDataIntegrityViolationByConstraint(String causeMessage, int status,
                                                                 String description) throws Exception {
        when(orderApiService.createOrder(any())).thenThrow(
                new DataIntegrityViolationException("could not execute statement",
                        new SQLIntegrityConstraintViolationException(causeMessage)));
        String content = """
                {
                    "internalId": "order-1",
                    "amount": 5000,
                    "methods":["CARD"],
                    "userId": "163637435086"
                }""";
        mockMvc.perform(
                        post("/api/v1/order")
                                .with(apiKeyOf(UUID.randomUUID()))
                                .content(content)
                                .header("Content-Type", "application/json")
                ).andExpect(status().is(status))
                .andExpect(jsonPath("$.description").value(description));
    }

    @Test
    @DisplayName("Метод должен вернуть, если отсутствует поле internalId.")
    void createOrder_shouldReturn400IfNoInternalId() throws Exception {
        String content = """
                {
                    "amount": 5000,
                    "methods":["CARD"],
                    "userId": "163637435086"
                }""";
        mockMvc.perform(
                        post("/api/v1/order")
                                .with(apiKeyOf(UUID.randomUUID()))
                                .content(content)
                                .header("Content-Type", "application/json")
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value("Поле «internalId» должно быть заполнено."));
    }

    @Test
    @DisplayName("Метод должен вернуть, если отсутствует поле bank.")
    void createOrder_shouldReturn400IfInternalIdIsBlank() throws Exception {
        String content = """
                {
                    "internalId": " ",
                    "amount": 5000,
                    "methods":["CARD"],
                    "userId": "163637435086"
                }""";
        mockMvc.perform(
                        post("/api/v1/order")
                                .with(apiKeyOf(UUID.randomUUID()))
                                .content(content)
                                .header("Content-Type", "application/json")
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value("Поле «internalId» должно быть заполнено."));
    }

    @Test
    @DisplayName("Метод должен вернуть, если отсутствует поле amount.")
    void createOrder_shouldReturn400IfNoAmount() throws Exception {
        String content = """
                {
                    "internalId": "cfcdf9db-58d0-4268-b2b3-aeb493bda45b",
                    "methods":["CARD"],
                    "userId": "163637435086"
                }""";
        mockMvc.perform(
                        post("/api/v1/order")
                                .with(apiKeyOf(UUID.randomUUID()))
                                .content(content)
                                .header("Content-Type", "application/json")
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value("Поле «amount» должно быть заполнено."));
    }

    @Test
    @DisplayName("Метод должен вернуть, если поле amount отрицательное.")
    void createOrder_shouldReturn400IfAmountIsNegative() throws Exception {
        String content = """
                {
                    "internalId": "cfcdf9db-58d0-4268-b2b3-aeb493bda45b",
                    "amount": -5000,
                    "methods":["CARD"],
                    "userId": "163637435086"
                }""";
        mockMvc.perform(
                        post("/api/v1/order")
                                .with(apiKeyOf(UUID.randomUUID()))
                                .content(content)
                                .header("Content-Type", "application/json")
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value("Поле «amount» должно быть больше нуля."));
    }

    @Test
    @DisplayName("Метод должен вернуть, если отсутствует поле methods.")
    void createOrder_shouldReturn400IfNoMethods() throws Exception {
        String content = """
                {
                    "internalId": "cfcdf9db-58d0-4268-b2b3-aeb493bda45b",
                    "amount": 5000,
                    "userId": "163637435086"
                }""";
        mockMvc.perform(
                        post("/api/v1/order")
                                .with(apiKeyOf(UUID.randomUUID()))
                                .content(content)
                                .header("Content-Type", "application/json")
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value("Поле «methods» должно содержать хотя бы одно значение."));
    }

    @Test
    @DisplayName("Метод должен вернуть, если массив methods пустой..")
    void createOrder_shouldReturn400IfMethodsIsEmpty() throws Exception {
        String content = """
                {
                    "internalId": "cfcdf9db-58d0-4268-b2b3-aeb493bda45b",
                    "amount": 5000,
                    "methods": [],
                    "userId": "163637435086"
                }""";
        mockMvc.perform(
                        post("/api/v1/order")
                                .with(apiKeyOf(UUID.randomUUID()))
                                .content(content)
                                .header("Content-Type", "application/json")
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value("Поле «methods» должно содержать хотя бы одно значение."));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "qwerty",
            "ftp://root/home/picture.png",
            "http://localhost:8080",
            "http://some-site.com/callback",
    })
    @DisplayName("Метод должен вернуть, если поле callbackUrl невалидный URL.")
    void createOrder_shouldReturn400IfCallbackUrlsIsNotValid(String callbackUrl) throws Exception {
        String content = """
                {
                    "internalId": "cfcdf9db-58d0-4268-b2b3-aeb493bda45b",
                    "amount": 5000,
                    "methods": ["CARD"],
                    "userId": "163637435086",
                    "callbackUrl": "%s"
                }""".formatted(callbackUrl);
        mockMvc.perform(
                        post("/api/v1/order")
                                .with(apiKeyOf(UUID.randomUUID()))
                                .content(content)
                                .header("Content-Type", "application/json")
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value("Поле «callbackUrl» должно быть корректным адресом с протоколом https."));
    }

    @ParameterizedTest
    @MethodSource("createOrderRequestArguments")
    @DisplayName("Метод должен 201, если ордер успешно создан..")
    void createOrder_shouldReturn201AndCallServiceAndReturnCreatedOrder(CreateOrderRequest expected) throws Exception {
        ArgumentCaptor<CreateOrderCommand> captor = ArgumentCaptor.forClass(CreateOrderCommand.class);
        UUID clientId = UUID.randomUUID();
        mockMvc.perform(
                post("/api/v1/order")
                        .with(apiKeyOf(clientId))
                        .content(objectMapper.writeValueAsString(expected))
                        .header("Content-Type", "application/json")
        ).andExpect(status().isCreated());
        verify(orderApiService).createOrder(captor.capture());
        CreateOrderCommand command = captor.getValue();
        CreateOrderRequest actual = command.request();
        assertAll(
                () -> assertEquals(clientId, command.clientId()),
                () -> assertEquals(30, command.timeoutSeconds()),
                () -> assertEquals(expected.internalId(), actual.internalId()),
                () -> assertEquals(expected.amount(), actual.amount()),
                () -> assertEquals(expected.methods(), actual.methods()),
                () -> assertEquals(expected.callbackUrl(), actual.callbackUrl()),
                () -> assertEquals(expected.userId(), actual.userId())
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 60, 5})
    @DisplayName("Заголовок с количеством секунд таймаута поиска должен быть передан в сервис.")
    void createOrder_shouldPassTimeoutSecondsFromHeader(Integer timeoutSeconds) throws Exception {
        String content = """
                {
                    "internalId": "cfcdf9db-58d0-4268-b2b3-aeb493bda45b",
                    "amount": 5000,
                    "methods": ["CARD"],
                    "userId": "163637435086",
                    "callbackUrl": "https://example.com/callback"
                }""";
        mockMvc.perform(
                post("/api/v1/order")
                        .with(apiKeyOf(UUID.randomUUID()))
                        .content(content)
                        .header("Content-Type", "application/json")
                        .header("Search-Timeout-Seconds", timeoutSeconds)
        ).andExpect(status().isCreated());
        verify(orderApiService).createOrder(argThat(command -> timeoutSeconds.equals(command.timeoutSeconds())));
    }

    @Test
    @DisplayName("Если заголовок в запросе отсутствует, то должно быть передано значение таймаута поиска реквизитов по умолчанию.")
    void createOrder_shouldPassDefaultTimeoutSeconds() throws Exception {
        String content = """
                {
                    "internalId": "cfcdf9db-58d0-4268-b2b3-aeb493bda45b",
                    "amount": 5000,
                    "methods": ["CARD"],
                    "userId": "163637435086",
                    "callbackUrl": "https://example.com/callback"
                }""";
        mockMvc.perform(
                post("/api/v1/order")
                        .with(apiKeyOf(UUID.randomUUID()))
                        .content(content)
                        .header("Content-Type", "application/json")
        ).andExpect(status().isCreated());
        verify(orderApiService).createOrder(argThat(command -> command.timeoutSeconds() == 30));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Значение заголовка Test-Order должно быть передано в сервис.")
    void createOrder_shouldPassTestOrderFromHeader(boolean testOrder) throws Exception {
        String content = """
                {
                    "internalId": "cfcdf9db-58d0-4268-b2b3-aeb493bda45b",
                    "amount": 5000,
                    "methods": ["CARD"],
                    "userId": "163637435086",
                    "callbackUrl": "https://example.com/callback"
                }""";
        mockMvc.perform(
                post("/api/v1/order")
                        .with(apiKeyOf(UUID.randomUUID()))
                        .content(content)
                        .header("Content-Type", "application/json")
                        .header("Test-Order", testOrder)
        ).andExpect(status().isCreated());
        verify(orderApiService).createOrder(argThat(command -> command.testOrder() == testOrder));
    }

    private static final String ASYNC_ORDER_CONTENT = """
            {
                "internalId": "cfcdf9db-58d0-4268-b2b3-aeb493bda45b",
                "amount": 5000,
                "methods": ["CARD"],
                "userId": "163637435086",
                "callbackUrl": "https://example.com/callback"
            }""";

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"SEARCHING", "CANCELED"})
    @DisplayName("Асинхронное создание ордера должно вызвать асинхронный метод сервиса и вернуть 202 с ордером от него.")
    void createOrder_shouldReturn202IfAsync(OrderStatus status) throws Exception {
        ClientOrderSummary expected = new ClientOrderSummary(UUID.randomUUID(), Instant.now(),
                UUID.randomUUID().toString(), status, 5000, null, null, null, "https://example.com/callback");
        when(orderApiService.createOrderAsync(any(), any())).thenReturn(expected);
        mockMvc.perform(
                        post("/api/v1/order")
                                .with(apiKeyOf(UUID.randomUUID()))
                                .content(ASYNC_ORDER_CONTENT)
                                .header("Content-Type", "application/json")
                                .header("Async-Request", true)
                ).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(expected.id().toString()))
                .andExpect(jsonPath("$.status").value(status.name()));
        verify(orderApiService).createOrderAsync(any(), any());
        verify(orderApiService, never()).createOrder(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", ""})
    @DisplayName("Если заголовок Async-Request отсутствует или равен false, ордер должен создаваться синхронно с ответом 201.")
    void createOrder_shouldReturn201IfNotAsync(String asyncHeader) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/order")
                .with(apiKeyOf(UUID.randomUUID()))
                .content(ASYNC_ORDER_CONTENT)
                .header("Content-Type", "application/json");
        if (!asyncHeader.isEmpty()) {
            request.header("Async-Request", asyncHeader);
        }
        mockMvc.perform(request).andExpect(status().isCreated());
        verify(orderApiService).createOrder(any());
        verify(orderApiService, never()).createOrderAsync(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Достигнут лимит одновременных поисков реквизитов.", "Лимит исчерпан."})
    @DisplayName("Если достигнут лимит поисков реквизитов, метод должен вернуть 503.")
    void createOrder_shouldReturn503IfSearchRejected(String message) throws Exception {
        when(orderApiService.createOrderAsync(any(), any())).thenThrow(new ServiceUnavailableException(message));
        mockMvc.perform(
                        post("/api/v1/order")
                                .with(apiKeyOf(UUID.randomUUID()))
                                .content(ASYNC_ORDER_CONTENT)
                                .header("Content-Type", "application/json")
                                .header("Async-Request", true)
                ).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.description").value("Обратитесь в поддержку."));
    }

    @ParameterizedTest
    @ValueSource(ints = {15, 45})
    @DisplayName("Если заголовок Test-Order отсутствует, то ордер должен создаваться как нетестовый.")
    void createOrder_shouldPassFalseTestOrderByDefault(Integer timeoutSeconds) throws Exception {
        String content = """
                {
                    "internalId": "cfcdf9db-58d0-4268-b2b3-aeb493bda45b",
                    "amount": 5000,
                    "methods": ["CARD"],
                    "userId": "163637435086",
                    "callbackUrl": "https://example.com/callback"
                }""";
        mockMvc.perform(
                post("/api/v1/order")
                        .with(apiKeyOf(UUID.randomUUID()))
                        .content(content)
                        .header("Content-Type", "application/json")
                        .header("Search-Timeout-Seconds", timeoutSeconds)
        ).andExpect(status().isCreated());
        verify(orderApiService).createOrder(argThat(command ->
                timeoutSeconds.equals(command.timeoutSeconds()) && !command.testOrder()));
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 0, -5})
    @DisplayName("Метод должен вернуть 400 без создания ордера, если таймаут поиска меньше 5 секунд.")
    void createOrder_shouldReturn400IfTimeoutTooSmall(Integer timeoutSeconds) throws Exception {
        String content = """
                {
                    "internalId": "cfcdf9db-58d0-4268-b2b3-aeb493bda45b",
                    "amount": 5000,
                    "methods": ["CARD"],
                    "userId": "163637435086",
                    "callbackUrl": "https://example.com/callback"
                }""";
        mockMvc.perform(
                post("/api/v1/order")
                        .with(apiKeyOf(UUID.randomUUID()))
                        .content(content)
                        .header("Content-Type", "application/json")
                        .header("Search-Timeout-Seconds", timeoutSeconds)
        ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.description").value("Таймаут поиска реквизитов не может быть меньше 5 секунд."));
        verifyNoInteractions(orderApiService);
    }

    @ParameterizedTest
    @ValueSource(ints = {61, 300})
    @DisplayName("Метод должен вернуть 400 без создания ордера, если таймаут поиска больше 60 секунд.")
    void createOrder_shouldReturn400IfTimeoutTooLarge(Integer timeoutSeconds) throws Exception {
        String content = """
                {
                    "internalId": "cfcdf9db-58d0-4268-b2b3-aeb493bda45b",
                    "amount": 5000,
                    "methods": ["CARD"],
                    "userId": "163637435086",
                    "callbackUrl": "https://example.com/callback"
                }""";
        mockMvc.perform(
                post("/api/v1/order")
                        .with(apiKeyOf(UUID.randomUUID()))
                        .content(content)
                        .header("Content-Type", "application/json")
                        .header("Search-Timeout-Seconds", timeoutSeconds)
        ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value("Таймаут поиска реквизитов не может превышать 60 секунд."));
        verifyNoInteractions(orderApiService);
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 60})
    @DisplayName("Метод должен создать ордер, если таймаут поиска от 5 до 60 секунд.")
    void createOrder_shouldCreateOrderIfTimeoutWithinLimit(Integer timeoutSeconds) throws Exception {
        String content = """
                {
                    "internalId": "cfcdf9db-58d0-4268-b2b3-aeb493bda45b",
                    "amount": 5000,
                    "methods": ["CARD"],
                    "userId": "163637435086",
                    "callbackUrl": "https://example.com/callback"
                }""";
        mockMvc.perform(
                post("/api/v1/order")
                        .with(apiKeyOf(UUID.randomUUID()))
                        .content(content)
                        .header("Content-Type", "application/json")
                        .header("Search-Timeout-Seconds", timeoutSeconds)
        ).andExpect(status().isCreated());
        verify(orderApiService).createOrder(argThat(command ->
                timeoutSeconds.equals(command.timeoutSeconds()) && !command.testOrder()));
    }

    static Stream<Arguments> createOrderRequestArguments() {
        return Stream.of(
                Arguments.of(new CreateOrderRequest("76f4cb46-54b7-471a-9834-0ead44a8b4f3", 5124,
                        Set.of(RequestMethod.SBP), null, null)),
                Arguments.of(new CreateOrderRequest("c0ce1a58-c5f1-4427-97fd-af10a0e2c97b", 1166,
                        Set.of(RequestMethod.CARD, RequestMethod.SBP),
                        "https://example.com/callback", "Q-123515"))
        );
    }

    @ParameterizedTest
    @MethodSource("orderSummaryArguments")
    @DisplayName("Метод должен вернуть JSON представление ордера, когда он создан.")
    void createOrder_shouldReturnCreatedOrder(ClientOrderSummary expected) throws Exception {
        when(orderApiService.createOrder(any())).thenReturn(expected);
        mockMvc.perform(
                        post("/api/v1/order")
                                .with(apiKeyOf(UUID.randomUUID()))
                                .content(objectMapper.writeValueAsString(new CreateOrderRequest(
                                        "76f4cb46-54b7-471a-9834-0ead44a8b4f3", 5124,
                                        Set.of(RequestMethod.SBP), null, null
                                )))
                                .header("Content-Type", "application/json")
                ).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.id").value(new CustomMatcher<String>("is valid UUID") {
                    @Override
                    public boolean matches(Object actual) {
                        return ((actual instanceof String) && !UUID.fromString((String) actual).toString().isBlank());
                    }
                }))
                .andExpect(jsonPath("$.createdAt").value(
                        expected.createdAt().toEpochMilli()
                ))
                .andExpect(jsonPath("$.internalId").value(expected.internalId()))
                .andExpect(jsonPath("$.status").value(expected.status().name()))
                .andExpect(jsonPath("$.amount").value(expected.amount()))
                .andExpect(jsonPath("$.method").value(expected.method().name()))
                .andExpect(jsonPath("$.details").value(expected.details()))
                .andExpect(jsonPath("$.bank").value(expected.bank()))
                .andExpect(jsonPath("$.callbackUrl").value(expected.callbackUrl()));
    }

    static Stream<Arguments> orderSummaryArguments() {
        return Stream.of(
                Arguments.of(new ClientOrderSummary(UUID.randomUUID(), Instant.now(), UUID.randomUUID().toString(),
                        OrderStatus.NEW, 5129, RequestMethod.CARD,
                        "1234 1234 1234 1234", "Альфа банк", "https://google.com/callback")),
                Arguments.of(new ClientOrderSummary(UUID.randomUUID(), Instant.now(), UUID.randomUUID().toString(),
                        OrderStatus.SUCCESS, 1250, RequestMethod.SBP,
                        "+78971233212", "T-BANK", "https://example.com/path/callback"))
        );
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "OPERATOR"})
    @DisplayName("Сессия пользователя поддержки без апи ключа должна получить 401.")
    void createOrder_shouldReturn401IfSupportUserSessionWithoutApiKey(Role role) throws Exception {
        mockMvc.perform(post("/api/v1/order")
                        .with(user(UUID.randomUUID().toString()).roles(role.name()))
                        .header("Content-Type", "application/json")
                        .content("""
                                {
                                    "internalId": "cfcdf9db-58d0-4268-b2b3-aeb493bda45b",
                                    "amount": 5000,
                                    "methods": ["CARD"],
                                    "userId": "163637435086",
                                    "callbackUrl": "https://example.com/callback"
                                }"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Метод должен вернуть 401, если заголовок Api-Key отсутствует.")
    void createOrder_shouldReturn401IfNoApiKey() throws Exception {
        mockMvc.perform(post("/api/v1/order")
                        .header("Content-Type", "application/json")
                        .content("""
                                {
                                    "internalId": "cfcdf9db-58d0-4268-b2b3-aeb493bda45b",
                                    "amount": 5000,
                                    "methods": ["CARD"],
                                    "userId": "163637435086",
                                    "callbackUrl": "https://example.com/callback"
                                }"""))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "   "
    })
    @DisplayName("Метод должен вернуть 401, если заголовок Api-Key пустой.")
    void createOrder_shouldReturn401IfApiKeyIsEmpty(String apiKey) throws Exception {
        mockMvc.perform(post("/api/v1/order")
                        .header("Content-Type", "application/json")
                        .header("Api-Key", apiKey)
                        .content("""
                                {
                                    "internalId": "cfcdf9db-58d0-4268-b2b3-aeb493bda45b",
                                    "amount": 5000,
                                    "methods": ["CARD"],
                                    "userId": "163637435086",
                                    "callbackUrl": "https://example.com/callback"
                                }"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Метод должен вернуть 401, если ключ по значению из заголовка Api-Key не найден..")
    void createOrder_shouldReturn401IfApiKeyNotFound() throws Exception {
        when(apiKeyService.findByKey(any())).thenReturn(Optional.empty());
        mockMvc.perform(post("/api/v1/order")
                        .header("Content-Type", "application/json")
                        .header("Api-Key", "iTTnB770i985XpfFWtJBYH1FXCLkCQxo")
                        .content("""
                                {
                                    "internalId": "cfcdf9db-58d0-4268-b2b3-aeb493bda45b",
                                    "amount": 5000,
                                    "methods": ["CARD"],
                                    "userId": "163637435086",
                                    "callbackUrl": "https://example.com/callback"
                                }"""))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "iTTnB770i985XpfFWtJBYH1FXCLkCQxo", "JSF0Fri8Go12IanPKGOTOf86bHwQCsaz"
    })
    @DisplayName("Метод должен вернуть 201, если ключ найден. Должен быть вызван метод с соответствующим идентификатором клиента.")
    void createOrder_shouldReturn201IfApiKeyAccepted(String headerApiKey) throws Exception {
        ApiKey apiKey = mock(ApiKey.class);
        when(apiKeyService.findByKey(headerApiKey)).thenReturn(Optional.of(apiKey));
        Client client = mock(Client.class);
        UUID clientId = UUID.randomUUID();
        when(client.getId()).thenReturn(clientId);
        when(apiKey.getClient()).thenReturn(client);
        mockMvc.perform(post("/api/v1/order")
                        .header("Content-Type", "application/json")
                        .header("Api-Key", headerApiKey)
                        .content("""
                                {
                                    "internalId": "cfcdf9db-58d0-4268-b2b3-aeb493bda45b",
                                    "amount": 5000,
                                    "methods": ["CARD"],
                                    "userId": "163637435086",
                                    "callbackUrl": "https://example.com/callback"
                                }"""))
                .andExpect(status().isCreated());
        verify(orderApiService).createOrder(argThat(command ->
                clientId.equals(command.clientId()) && command.timeoutSeconds() == 30));
    }

    @RepeatedTest(value = 2)
    @DisplayName("Должен быть вызван метод сервиса.")
    void cancelOrder_shouldCallServiceMethod() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        mockMvc.perform(patch("/api/v1/order/" + orderId)
                .with(apiKeyOf(clientId)));
        verify(orderApiService).cancelOrder(clientId, orderId);
    }

    @ParameterizedTest
    @MethodSource("orderSummaryArguments")
    @DisplayName("Метод должен вернуть JSON представление ордера, когда он создан.")
    void getOrder_shouldReturnOrder(ClientOrderSummary expected) throws Exception {
        when(orderApiService.findByIdAndClientId(any(), any())).thenReturn(expected);
        mockMvc.perform(
                        MockMvcRequestBuilders.get("/api/v1/order/" + expected.id())
                                .with(apiKeyOf(UUID.randomUUID()))
                                .content(objectMapper.writeValueAsString(new CreateOrderRequest(
                                        "76f4cb46-54b7-471a-9834-0ead44a8b4f3", 5124,
                                        Set.of(RequestMethod.SBP), null, null
                                )))
                                .header("Content-Type", "application/json")
                ).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.id").value(new CustomMatcher<String>("is valid UUID") {
                    @Override
                    public boolean matches(Object actual) {
                        return ((actual instanceof String) && !UUID.fromString((String) actual).toString().isBlank());
                    }
                }))
                .andExpect(jsonPath("$.createdAt").value(
                        expected.createdAt().toEpochMilli()
                ))
                .andExpect(jsonPath("$.internalId").value(expected.internalId()))
                .andExpect(jsonPath("$.status").value(expected.status().name()))
                .andExpect(jsonPath("$.amount").value(expected.amount()))
                .andExpect(jsonPath("$.method").value(expected.method().name()))
                .andExpect(jsonPath("$.details").value(expected.details()))
                .andExpect(jsonPath("$.bank").value(expected.bank()))
                .andExpect(jsonPath("$.callbackUrl").value(expected.callbackUrl()));
    }

    @Test
    @DisplayName("Должен быть вызван метод сервиса и возвращена страница списка ордеров.")
    void getOrders_shouldCallServiceMethodAndReturnContentWithPagination() throws Exception {
        ClientOrderSummary expected = new ClientOrderSummary(UUID.randomUUID(), Instant.now(), UUID.randomUUID().toString(),
                OrderStatus.NEW, 5129, RequestMethod.CARD,
                "1234 1234 1234 1234", "Сбербанк", "https://google.com/callback");
        when(orderApiService.findAll(any(), any(), any())).thenReturn(new PageImpl<>(List.of(expected)));
        mockMvc.perform(get("/api/v1/order")
                        .with(apiKeyOf(UUID.randomUUID()))
                        .header("Content-Type", "application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").exists())
                .andExpect(jsonPath("$.page.size").value(1))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].id").isNotEmpty())
                .andExpect(jsonPath("$.content[0].id").value(new CustomMatcher<String>("is valid UUID") {
                    @Override
                    public boolean matches(Object actual) {
                        return ((actual instanceof String) && !UUID.fromString((String) actual).toString().isBlank());
                    }
                }))
                .andExpect(jsonPath("$.content[0].createdAt").value(
                        expected.createdAt().toEpochMilli()
                ))
                .andExpect(jsonPath("$.content[0].internalId").value(expected.internalId()))
                .andExpect(jsonPath("$.content[0].status").value(expected.status().name()))
                .andExpect(jsonPath("$.content[0].amount").value(expected.amount()))
                .andExpect(jsonPath("$.content[0].method").value(expected.method().name()))
                .andExpect(jsonPath("$.content[0].details").value(expected.details()))
                .andExpect(jsonPath("$.content[0].bank").value(expected.bank()))
                .andExpect(jsonPath("$.content[0].callbackUrl").value(expected.callbackUrl()));
    }

    @ParameterizedTest
    @CsvSource({"CRYPTO", "QR"})
    @DisplayName("Метод должен вернуть 400 с названием поля, если в methods передан неизвестный метод.")
    void createOrder_shouldReturnBadRequestOnUnknownMethod(String method) throws Exception {
        String content = """
                {
                    "internalId": "order-1",
                    "amount": 5000,
                    "methods": ["%s"]
                }""".formatted(method);
        mockMvc.perform(post("/api/v1/order")
                        .with(apiKeyOf(UUID.randomUUID()))
                        .content(content)
                        .header("Content-Type", "application/json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").doesNotExist())
                .andExpect(jsonPath("$.timestamp").isNumber())
                .andExpect(jsonPath("$.description")
                        .value("Поле «methods» содержит некорректное значение «" + method + "»."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{bad", "[1, 2"})
    @DisplayName("Метод должен вернуть 400, если тело не является корректным JSON.")
    void createOrder_shouldReturnBadRequestOnMalformedJson(String content) throws Exception {
        mockMvc.perform(post("/api/v1/order")
                        .with(apiKeyOf(UUID.randomUUID()))
                        .content(content)
                        .header("Content-Type", "application/json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value("Тело запроса содержит некорректные данные."));
    }

    @ParameterizedTest
    @CsvSource({
            "Search-Timeout-Seconds,abc",
            "Test-Order,maybe",
            "Async-Request,12"
    })
    @DisplayName("Метод должен вернуть 400 с названием заголовка, если значение заголовка не преобразуется к типу.")
    void createOrder_shouldReturnBadRequestOnInvalidHeader(String header, String value) throws Exception {
        String content = """
                {
                    "internalId": "order-1",
                    "amount": 5000,
                    "methods": ["CARD"]
                }""";
        mockMvc.perform(post("/api/v1/order")
                        .with(apiKeyOf(UUID.randomUUID()))
                        .content(content)
                        .header("Content-Type", "application/json")
                        .header(header, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").doesNotExist())
                .andExpect(jsonPath("$.timestamp").isNumber())
                .andExpect(jsonPath("$.description")
                        .value("Параметр «" + header + "» содержит некорректное значение «" + value + "»."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/plain", "application/xml"})
    @DisplayName("Метод должен вернуть 415, если тип содержимого не JSON.")
    void createOrder_shouldReturnUnsupportedMediaType(String contentType) throws Exception {
        mockMvc.perform(post("/api/v1/order")
                        .with(apiKeyOf(UUID.randomUUID()))
                        .content("{}")
                        .header("Content-Type", contentType))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail").doesNotExist())
                .andExpect(jsonPath("$.description")
                        .value("Тип содержимого «" + contentType + "» не поддерживается."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "123"})
    @DisplayName("Получение и отмена ордера должны вернуть 400, если идентификатор не UUID.")
    void getAndCancelOrder_shouldReturnBadRequestOnInvalidId(String id) throws Exception {
        String description = "Параметр «id» содержит некорректное значение «" + id + "».";
        mockMvc.perform(get("/api/v1/order/" + id).with(apiKeyOf(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value(description));
        mockMvc.perform(patch("/api/v1/order/" + id).with(apiKeyOf(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value(description));
    }

    @ParameterizedTest
    @CsvSource({
            "status,PAID",
            "method,CRYPTO"
    })
    @DisplayName("Список ордеров должен вернуть 400, если в фильтре неизвестное значение.")
    void getOrders_shouldReturnBadRequestOnUnknownFilterValue(String param, String value) throws Exception {
        mockMvc.perform(get("/api/v1/order").param(param, value).with(apiKeyOf(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description")
                        .value("Поле «" + param + "» содержит некорректное значение «" + value + "»."));
    }

    static Stream<Arguments> getOrders_shouldBindMultipleFilterValues() {
        return Stream.of(
                Arguments.of(new String[]{"NEW"}, new String[]{"SBP"},
                        Set.of(OrderStatus.NEW), Set.of(RequestMethod.SBP)),
                Arguments.of(new String[]{"SUCCESS", "TIMEOUT"}, new String[]{"CARD", "SBP"},
                        Set.of(OrderStatus.SUCCESS, OrderStatus.TIMEOUT), Set.of(RequestMethod.CARD, RequestMethod.SBP))
        );
    }

    @ParameterizedTest
    @MethodSource
    @DisplayName("Список ордеров должен принять несколько значений статуса и метода.")
    void getOrders_shouldBindMultipleFilterValues(String[] statuses, String[] methods,
                                                  Set<OrderStatus> expectedStatuses,
                                                  Set<RequestMethod> expectedMethods) throws Exception {
        UUID clientId = UUID.randomUUID();
        when(orderApiService.findAll(eq(clientId), any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/order").param("status", statuses).param("method", methods)
                        .with(apiKeyOf(clientId)))
                .andExpect(status().isOk());

        ArgumentCaptor<ClientOrderFilter> captor = ArgumentCaptor.forClass(ClientOrderFilter.class);
        verify(orderApiService).findAll(eq(clientId), captor.capture(), any());
        assertEquals(expectedStatuses, captor.getValue().status());
        assertEquals(expectedMethods, captor.getValue().method());
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "method"})
    @DisplayName("Список ордеров не должен фильтровать по параметру с пустым значением.")
    void getOrders_shouldIgnoreEmptyFilterValue(String param) throws Exception {
        UUID clientId = UUID.randomUUID();
        when(orderApiService.findAll(eq(clientId), any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/order").param(param, "").with(apiKeyOf(clientId)))
                .andExpect(status().isOk());

        ArgumentCaptor<ClientOrderFilter> captor = ArgumentCaptor.forClass(ClientOrderFilter.class);
        verify(orderApiService).findAll(eq(clientId), captor.capture(), any());
        ClientOrderFilter filter = captor.getValue();
        assertTrue(Optional.ofNullable(filter.status()).map(Set::isEmpty).orElse(true));
        assertTrue(Optional.ofNullable(filter.method()).map(Set::isEmpty).orElse(true));
    }
}
