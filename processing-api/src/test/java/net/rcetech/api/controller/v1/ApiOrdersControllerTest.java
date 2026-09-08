package net.rcetech.api.controller.v1;

import net.rcetech.api.dto.CreateOrderRequest;
import net.rcetech.api.service.OrderApiService;
import net.rcetech.domain.mapping.orders.OrderMapper;
import net.rcetech.domain.model.orders.Order;
import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.config.ProcessingConfigurationProperties;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.RequestMethod;
import net.rcetech.meta.orders.dto.OrderSummary;
import org.hamcrest.CustomMatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiOrdersController.class)
@Import({MetaSecurityConfig.class})
@EnableConfigurationProperties(ProcessingConfigurationProperties.class)
class ApiOrdersControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderApiService orderApiService;

    @MockitoBean
    private OrderMapper orderMapper;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Метод должен вернуть 400 если отсутствует тело.")
    void createOrder_shouldReturn400IfNoBody() throws Exception {
        mockMvc.perform(post("/api/v1/order")
                .with(user("someClient").roles("CLIENT"))
                .with(csrf()))
                        .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Failed to read request"))
                .andExpect(jsonPath("$.instance").value("/api/v1/order"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNumber())
                .andExpect(jsonPath("$.description")
                        .value("The request body was expected but is missing. Please check the request body."));
    }

    @Test
    @DisplayName("Метод должен вернуть, если отсутствует поле internalId.")
    void createOrder_shouldReturn400IfNoInternalId() throws Exception {
        String content = """
                {
                    "amount": 5000,
                    "methods":["CARD"],
                    "enableUniqueAmount": true,
                    "userId": "163637435086"
                }""";
        mockMvc.perform(
                post("/api/v1/order")
                        .with(user("test").roles("CLIENT"))
                        .with(csrf())
                        .content(content)
                        .header("Content-Type", "application/json")
        ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value("field 'internalId' should be not blank;"));
    }

    @Test
    @DisplayName("Метод должен вернуть, если отсутствует поле bank.")
    void createOrder_shouldReturn400IfInternalIdIsBlank() throws Exception {
        String content = """
                {
                    "internalId": " ",
                    "amount": 5000,
                    "methods":["CARD"],
                    "enableUniqueAmount": true,
                    "userId": "163637435086"
                }""";
        mockMvc.perform(
                        post("/api/v1/order")
                                .with(user("test").roles("CLIENT"))
                                .with(csrf())
                                .content(content)
                                .header("Content-Type", "application/json")
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value("field 'internalId' should be not blank;"));
    }

    @Test
    @DisplayName("Метод должен вернуть, если отсутствует поле amount.")
    void createOrder_shouldReturn400IfNoAmount() throws Exception {
        String content = """
                {
                    "internalId": "cfcdf9db-58d0-4268-b2b3-aeb493bda45b",
                    "methods":["CARD"],
                    "enableUniqueAmount": true,
                    "userId": "163637435086"
                }""";
        mockMvc.perform(
                        post("/api/v1/order")
                                .with(user("test").roles("CLIENT"))
                                .with(csrf())
                                .content(content)
                                .header("Content-Type", "application/json")
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value("field 'amount' should be not empty;"));
    }

    @Test
    @DisplayName("Метод должен вернуть, если поле amount отрицательное.")
    void createOrder_shouldReturn400IfAmountIsNegative() throws Exception {
        String content = """
                {
                    "internalId": "cfcdf9db-58d0-4268-b2b3-aeb493bda45b",
                    "amount": -5000,
                    "methods":["CARD"],
                    "enableUniqueAmount": true,
                    "userId": "163637435086"
                }""";
        mockMvc.perform(
                        post("/api/v1/order")
                                .with(user("test").roles("CLIENT"))
                                .with(csrf())
                                .content(content)
                                .header("Content-Type", "application/json")
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value("field 'amount' should be positive;"));
    }

    @Test
    @DisplayName("Метод должен вернуть, если отсутствует поле methods.")
    void createOrder_shouldReturn400IfNoMethods() throws Exception {
        String content = """
                {
                    "internalId": "cfcdf9db-58d0-4268-b2b3-aeb493bda45b",
                    "amount": 5000,
                    "enableUniqueAmount": true,
                    "userId": "163637435086"
                }""";
        mockMvc.perform(
                        post("/api/v1/order")
                                .with(user("test").roles("CLIENT"))
                                .with(csrf())
                                .content(content)
                                .header("Content-Type", "application/json")
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value("field 'methods' should contains at least one;"));
    }

    @Test
    @DisplayName("Метод должен вернуть, если массив methods пустой..")
    void createOrder_shouldReturn400IfMethodsIsEmpty() throws Exception {
        String content = """
                {
                    "internalId": "cfcdf9db-58d0-4268-b2b3-aeb493bda45b",
                    "amount": 5000,
                    "methods": [],
                    "enableUniqueAmount": true,
                    "userId": "163637435086"
                }""";
        mockMvc.perform(
                        post("/api/v1/order")
                                .with(user("test").roles("CLIENT"))
                                .with(csrf())
                                .content(content)
                                .header("Content-Type", "application/json")
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value("field 'methods' should contains at least one;"));
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
                    "enableUniqueAmount": true,
                    "userId": "163637435086",
                    "callbackUrl": "%s"
                }""".formatted(callbackUrl);
        mockMvc.perform(
                        post("/api/v1/order")
                                .with(user("test").roles("CLIENT"))
                                .with(csrf())
                                .content(content)
                                .header("Content-Type", "application/json")
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value("field 'callbackUrl' should be valid and starts with https;"));
    }

    @ParameterizedTest
    @MethodSource("createOrderRequestArguments")
    @DisplayName("Метод должен 201, если ордер успешно создан..")
    void createOrder_shouldReturn201AndCallServiceAndReturnCreatedOrder(CreateOrderRequest expected) throws Exception {
        ArgumentCaptor<CreateOrderRequest> captor = ArgumentCaptor.forClass(CreateOrderRequest.class);
        UUID clientId = UUID.randomUUID();
        mockMvc.perform(
                        post("/api/v1/order")
                                .with(user(clientId.toString()).roles("CLIENT"))
                                .with(csrf())
                                .content(objectMapper.writeValueAsString(expected))
                                .header("Content-Type", "application/json")
                ).andExpect(status().isCreated());
        verify(orderApiService).createOrder(eq(clientId), captor.capture());
        CreateOrderRequest actual = captor.getValue();
        assertAll(
                () -> assertEquals(expected.internalId(), actual.internalId()),
                () -> assertEquals(expected.amount(), actual.amount()),
                () -> assertEquals(expected.methods(), actual.methods()),
                () -> assertEquals(expected.enableUniqueAmount(), actual.enableUniqueAmount()),
                () -> assertEquals(expected.callbackUrl(), actual.callbackUrl()),
                () -> assertEquals(expected.userId(), actual.userId())
        );
    }

    static Stream<Arguments> createOrderRequestArguments() {
        return Stream.of(
                Arguments.of(new CreateOrderRequest("76f4cb46-54b7-471a-9834-0ead44a8b4f3", 5124,
                        Set.of(RequestMethod.SBP), false, null, null)),
                Arguments.of(new CreateOrderRequest("c0ce1a58-c5f1-4427-97fd-af10a0e2c97b", 1166,
                        Set.of(RequestMethod.CARD, RequestMethod.SBP), true,
                        "https://example.com/callback", "Q-123515"))
        );
    }

    @ParameterizedTest
    @MethodSource("orderSummaryArguments")
    @DisplayName("Метод должен вернуть JSON представление ордера, когда он создан.")
    void createOrder_shouldReturnCreatedOrder(OrderSummary expected) throws Exception {
        Order order = mock(Order.class);
        when(orderApiService.createOrder(any(), any())).thenReturn(order);
        when(orderMapper.toOrderSummary(order)).thenReturn(expected);
        mockMvc.perform(
                post("/api/v1/order")
                        .with(user(UUID.randomUUID().toString()).roles("CLIENT"))
                        .with(csrf())
                        .content(objectMapper.writeValueAsString(new CreateOrderRequest(
                                "76f4cb46-54b7-471a-9834-0ead44a8b4f3", 5124,
                                Set.of(RequestMethod.SBP), false, null, null
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
                        LocalDateTime.ofInstant(expected.createdAt(), ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))
                ))
                .andExpect(jsonPath("$.internalId").value(expected.internalId()))
                .andExpect(jsonPath("$.status").value(expected.status().name()))
                .andExpect(jsonPath("$.amount").value(expected.amount()))
                .andExpect(jsonPath("$.enableUniqueAmount").value(expected.enableUniqueAmount()))
                .andExpect(jsonPath("$.callbackUrl").value(expected.callbackUrl()));
    }

    static Stream<Arguments> orderSummaryArguments() {
        return Stream.of(
                Arguments.of(new OrderSummary(UUID.randomUUID(), Instant.now(), UUID.randomUUID().toString(),
                        OrderStatus.NEW, 5129, true, "https://google.com/callback")),
                Arguments.of(new OrderSummary(UUID.randomUUID(), Instant.now(), UUID.randomUUID().toString(),
                        OrderStatus.SUCCESS, 1250, false, "https://example.com/path/callback"))
        );
    }

    @RepeatedTest(value = 2)
    @DisplayName("Должен быть вызван метод сервиса.")
    void cancelOrder_shouldCallServiceMethod() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        mockMvc.perform(patch("/api/v1/order/" + orderId)
                .with(csrf())
                .with(user(clientId.toString()).roles("CLIENT")));
        verify(orderApiService).cancelOrder(clientId, orderId);
    }
}
