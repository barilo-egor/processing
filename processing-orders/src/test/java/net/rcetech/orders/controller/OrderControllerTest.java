package net.rcetech.orders.controller;

import net.rcetech.clients.config.ClientsSecurityConfig;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.config.ProcessingConfigurationProperties;
import net.rcetech.meta.exception.OrderStatusUpdateException;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusChange;
import net.rcetech.meta.orders.OrderStatusTransition;
import net.rcetech.meta.orders.RequestMethod;
import net.rcetech.meta.orders.dto.OrderResponse;
import net.rcetech.meta.orders.dto.OrderSummary;
import net.rcetech.orders.OrderFacade;
import org.hamcrest.CustomMatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({ClientsSecurityConfig.class, MetaSecurityConfig.class})
@EnableConfigurationProperties(ProcessingConfigurationProperties.class)
class OrderControllerTest {

    private static final CustomMatcher<String> UUID_MATCHER = new CustomMatcher<>("is valid UUID") {
        @Override
        public boolean matches(Object actual) {
            return ((actual instanceof String) && !UUID.fromString((String) actual).toString().isBlank());
        }
    };

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private OrderFacade orderFacade;

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @CsvSource({
            "6e299aaf-4d82-438a-85bc-e2f8bcbadbfb,1789120584024,smokilolik,67a59d37-15f4-4f63-ac3f-d7337fb8e4f1," +
                    "96f457ff-5f2e-4415-a49c-745ba920bf25,NEW,5450,ALFA_TEAM,15936",
            "05aa6142-6d22-42f3-99ff-93c9240858b5,1789120591362,TGSHOP,ae9cfde3-b11b-4b11-99b2-32194d77897a," +
                    "5234975,SUCCESS,2500,EVO_PAY,f8c7d994-b315-4608-89c6-602548a7c279"
    })
    @DisplayName("Метод должен вернуть статус 200 с JSON ордерами.")
    void getOrders_ShouldReturn200WithOrders(UUID id, Long millis, String clientUsername, UUID clientId, String internalId,
                                             OrderStatus status, Integer amount, Merchant merchant, String merchantOrderId) throws Exception {
        OrderSummary orderSummary =  new OrderSummary() {
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public Instant getCreatedAt() {
                return Instant.ofEpochMilli(millis);
            }

            @Override
            public UUID getClientId() {
                return clientId;
            }

            @Override
            public String getClientUsername() {
                return clientUsername;
            }

            @Override
            public String getInternalId() {
                return internalId;
            }

            @Override
            public OrderStatus getStatus() {
                return status;
            }

            @Override
            public Integer getAmount() {
                return amount;
            }

            @Override
            public Merchant getMerchant() {
                return merchant;
            }

            @Override
            public String getMerchantOrderId() {
                return merchantOrderId;
            }
        };
        when(orderService.findAll(any(), any(), any())).thenReturn(new PageImpl<>(List.of(orderSummary)));
        mockMvc.perform(get("/api/private/order")
                        .with(user("fe795642-3aba-45b2-84a0-c69c07673004").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isNotEmpty())
                .andExpect(jsonPath("$.content[0].id").value(UUID_MATCHER))
                .andExpect(jsonPath("$.content[0].createdAt").value(millis))
                .andExpect(jsonPath("$.content[0].clientUsername").value(clientUsername))
                .andExpect(jsonPath("$.content[0].internalId").value(internalId))
                .andExpect(jsonPath("$.content[0].status").value(status.name()))
                .andExpect(jsonPath("$.content[0].amount").value(amount))
                .andExpect(jsonPath("$.page").exists())
                .andExpect(jsonPath("$.page.size").value(1))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLIENT", "USER"})
    @DisplayName("Метод должен вернуть статус 403, если запрос выполняется не админ или оператор.")
    void getOrders_ShouldReturn403IfNotAdminOrOperator(String role) throws Exception {
        mockMvc.perform(get("/api/private/order")
                        .with(user("fe795642-3aba-45b2-84a0-c69c07673004").roles(role))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "c63c69fb-e14c-47af-b64e-20bd1faf9c7a,1789122084461,1789122085461,1b3cd577-02a3-4f38-bad2-bfd98fb4e108," +
                    "smokilolik,1245670,NEW,5400,ALFA_TEAM,17457424,PROCESSING,CARD,1234 1234 1234 1234,Альфа-Банк," +
                    "https://example.com/callback",
            "33182fc8-ab84-43f6-847d-67c1d8679bbd,1789122089767,1789122090767,9d429ff1-e362-409a-b282-33a33856f20e," +
                    "TG_SHOP,b1d61aa7-eb4c-40ce-a458-9192294e5ca0,SUCCESS,1200,ONLY_PAYS,b65aecec-781c-420f-a4b9-433f1da03ef4" +
                    ",ACCEPTED,SBP,+78957623243,T-BANK,null"
    }, nullValues = {"null"})
    @DisplayName("Метод должен вернуть статус 200 и JSON ордера.")
    void getOrder_shouldReturn200WithOrder(UUID id, Long createdAt, Long expiresAt, UUID clientId, String clientUsername,
                                           String internalId, OrderStatus status, Integer amount,
                                           Merchant merchant, String merchantOrderId,
                                           String merchantOrderStatus, RequestMethod method, String details,
                                           String bank, String callbackUrl) throws Exception {
        OrderResponse orderResponse = new OrderResponse(id, Instant.ofEpochMilli(createdAt), Instant.ofEpochMilli(expiresAt),
                clientId, clientUsername, internalId, status, amount, merchant, merchantOrderId,
                merchantOrderStatus, method, details, bank, callbackUrl);
        when(orderService.findById(any(), any())).thenReturn(Optional.of(orderResponse));
        mockMvc.perform(get("/api/private/order/" + id.toString())
                        .with(user("fe795642-3aba-45b2-84a0-c69c07673004").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(UUID_MATCHER))
                .andExpect(jsonPath("$.createdAt").value(createdAt))
                .andExpect(jsonPath("$.expiresAt").value(expiresAt))
                .andExpect(jsonPath("$.clientId").value(UUID_MATCHER))
                .andExpect(jsonPath("$.amount").value(amount))
                .andExpect(jsonPath("$.merchant").value(merchant.name()))
                .andExpect(jsonPath("$.merchantOrderId").value(merchantOrderId))
                .andExpect(jsonPath("$.merchantOrderStatus").value(merchantOrderStatus))
                .andExpect(jsonPath("$.method").value(method.name()))
                .andExpect(jsonPath("$.details").value(details))
                .andExpect(jsonPath("$.bank").value(bank))
                .andExpect(jsonPath("$.callbackUrl").value(callbackUrl));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "b64f3554-0d38-4a31-ade0-daa8e7032e37",
            "0891d19b-dd94-4bca-9d61-9791a4534ee4"
    })
    @DisplayName("Метод должен вернуть статус 400, если ордер не найден.")
    void getOrderShouldReturn400IfOrderNotFound(UUID id) throws Exception {
        when(orderService.findById(any(), any())).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/private/order/" + id.toString())
                .with(user("fe795642-3aba-45b2-84a0-c69c07673004").roles("ADMIN"))
                .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @CsvSource({
            "7b0f1c52-4f3e-4d8a-9a51-2f6c1d9e8a10,NEW,SUCCESS,ADMIN",
            "e3a9d6b4-1c2f-4b7e-8d05-6a4f9c3b2e71,DISPUTE,CANCELED,OPERATOR",
            "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d,TIMEOUT,DISPUTE,OPERATOR"
    })
    @DisplayName("Метод должен вернуть статус 200 и направить ордер по маршруту в переданный статус.")
    void update_shouldReturn200AndRouteOrder(UUID id, OrderStatus statusFrom, OrderStatus status, String role)
            throws Exception {
        when(orderService.existsById(id)).thenReturn(true);
        when(orderFacade.routeOrder(eq(status), eq(id), isNull()))
                .thenReturn(new OrderStatusChange(statusFrom, status, OrderStatusTransition.ALLOWED));
        mockMvc.perform(patch("/api/private/order/" + id)
                        .with(user("fe795642-3aba-45b2-84a0-c69c07673004").roles(role))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status.name() + "\"}"))
                .andExpect(status().isOk());
        verify(orderFacade).routeOrder(eq(status), eq(id), isNull());
    }

    @ParameterizedTest
    @CsvSource({
            "0c1d2e3f-4a5b-4c6d-8e7f-9a0b1c2d3e4f,SUCCESS,CANCELED",
            "9f8e7d6c-5b4a-4398-a7b6-c5d4e3f2a1b0,SUCCESS,DISPUTE"
    })
    @DisplayName("Метод должен вернуть статус 400, если маршрут ордера невозможен.")
    void update_shouldReturn400IfRouteRejected(UUID id, OrderStatus statusFrom, OrderStatus statusTo) throws Exception {
        when(orderService.existsById(id)).thenReturn(true);
        doThrow(new OrderStatusUpdateException("Переход запрещён.", statusFrom, statusTo))
                .when(orderFacade).routeOrder(eq(statusTo), eq(id), isNull());
        mockMvc.perform(patch("/api/private/order/" + id)
                        .with(user("fe795642-3aba-45b2-84a0-c69c07673004").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + statusTo.name() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @CsvSource({
            "1f2e3d4c-5b6a-4798-8a7b-6c5d4e3f2a1b,SUCCESS,SUCCESS,Ордер уже в статусе «Успешный».",
            "8e9f0a1b-2c3d-4e5f-9a6b-7c8d9e0f1a2b,CANCELED,CANCELED,Ордер уже в статусе «Отменен».",
            "4d5e6f7a-8b9c-4d0e-8f1a-2b3c4d5e6f7a,DISPUTE,DISPUTE,Ордер уже в статусе «В споре».",
            "2a3b4c5d-6e7f-4a8b-9c0d-1e2f3a4b5c6d,TIMEOUT,CANCELED,Переход ордера из статуса «Просрочен» в статус «Отменен» не выполняется."
    })
    @DisplayName("Метод должен вернуть статус 400, если переход ордера пропущен.")
    void update_shouldReturn400IfTransitionSkipped(UUID id, OrderStatus statusFrom, OrderStatus statusTo,
                                                   String expectedDescription) throws Exception {
        when(orderService.existsById(id)).thenReturn(true);
        when(orderFacade.routeOrder(eq(statusTo), eq(id), isNull()))
                .thenReturn(new OrderStatusChange(statusFrom, statusTo, OrderStatusTransition.SKIPPED));
        mockMvc.perform(patch("/api/private/order/" + id)
                        .with(user("fe795642-3aba-45b2-84a0-c69c07673004").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + statusTo.name() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value(expectedDescription));
    }

    @ParameterizedTest
    @CsvSource({
            "3e4f5a6b-7c8d-4e9f-a0b1-c2d3e4f5a6b7,SUCCESS",
            "b7a6f5e4-d3c2-4b1a-9f0e-d8c7b6a5f4e3,CANCELED"
    })
    @DisplayName("Метод должен вернуть статус 400, если ордер не найден.")
    void update_shouldReturn400IfOrderNotFound(UUID id, OrderStatus status) throws Exception {
        when(orderService.existsById(id)).thenReturn(false);
        mockMvc.perform(patch("/api/private/order/" + id)
                        .with(user("fe795642-3aba-45b2-84a0-c69c07673004").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status.name() + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(orderFacade);
    }

    @ParameterizedTest
    @CsvSource({
            "6a7b8c9d-0e1f-4a2b-8c3d-4e5f6a7b8c9d,NEW",
            "d9c8b7a6-f5e4-4d3c-9b2a-1f0e9d8c7b6a,TIMEOUT"
    })
    @DisplayName("Метод должен вернуть статус 400, если статус не входит в список допустимых для ручной смены.")
    void update_shouldReturn400IfStatusNotAllowed(UUID id, OrderStatus status) throws Exception {
        when(orderService.existsById(id)).thenReturn(true);
        mockMvc.perform(patch("/api/private/order/" + id)
                        .with(user("fe795642-3aba-45b2-84a0-c69c07673004").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status.name() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value("field 'status' Недопустимый статус ордера.;"));
        verifyNoInteractions(orderFacade);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":null}", "{\"status\":\"PAID\"}"})
    @DisplayName("Метод должен вернуть статус 400, если статус в теле запроса отсутствует или некорректен.")
    void update_shouldReturn400IfStatusInvalid(String body) throws Exception {
        mockMvc.perform(patch("/api/private/order/" + UUID.randomUUID())
                        .with(user("fe795642-3aba-45b2-84a0-c69c07673004").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(orderFacade);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLIENT", "USER"})
    @DisplayName("Метод должен вернуть статус 403, если запрос выполняется не админ или оператор.")
    void update_shouldReturn403IfNotAdminOrOperator(String role) throws Exception {
        mockMvc.perform(patch("/api/private/order/" + UUID.randomUUID())
                        .with(user("fe795642-3aba-45b2-84a0-c69c07673004").roles(role))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUCCESS\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(orderFacade);
    }
}
