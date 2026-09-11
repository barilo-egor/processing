package net.rcetech.orders.controller;

import net.rcetech.clients.config.ClientsSecurityConfig;
import net.rcetech.domain.service.orders.OrderService;
import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.config.ProcessingConfigurationProperties;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.RequestMethod;
import net.rcetech.meta.orders.dto.OrderResponse;
import net.rcetech.meta.orders.dto.OrderSummary;
import org.hamcrest.CustomMatcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tgb.cryptoexchange.commons.enums.Merchant;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @CsvSource("""
            6e299aaf-4d82-438a-85bc-e2f8bcbadbfb,1789120584024,smokilolik,96f457ff-5f2e-4415-a49c-745ba920bf25,NEW,5450
            05aa6142-6d22-42f3-99ff-93c9240858b5,1789120591362,TGSHOP,5234975,SUCCESS,2500
            """)
    void getOrders_ShouldReturn200WithOrders(UUID id, Long millis, String clientUsername, String internalId,
                                             OrderStatus status, Integer amount) throws Exception {
        OrderSummary orderSummary = new OrderSummary(
                id,
                Instant.ofEpochMilli(millis),
                clientUsername,
                internalId,
                status,
                amount
        );
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
    void getOrders_ShouldReturn403IfNotAdminOrOperator(String role) throws Exception {
        mockMvc.perform(get("/api/private/order")
                        .with(user("fe795642-3aba-45b2-84a0-c69c07673004").roles(role))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "c63c69fb-e14c-47af-b64e-20bd1faf9c7a,1789122084461,1789122085461,1b3cd577-02a3-4f38-bad2-bfd98fb4e108," +
                    "1245670,NEW,5400,true,ALFA_TEAM,17457424,PROCESSING,CARD,1234 1234 1234 1234,Альфа-Банк," +
                    "https://example.com/callback",
            "33182fc8-ab84-43f6-847d-67c1d8679bbd,1789122089767,1789122090767,9d429ff1-e362-409a-b282-33a33856f20e," +
                    "b1d61aa7-eb4c-40ce-a458-9192294e5ca0,SUCCESS,1200,false,ONLY_PAYS,b65aecec-781c-420f-a4b9-433f1da03ef4" +
                    ",ACCEPTED,SBP,+78957623243,T-BANK,null"
    }, nullValues = {"null"})
    void getOrder_shouldReturn200WithOrder(UUID id, Long createdAt, Long expiresAt, UUID clientId,
                                           String internalId, OrderStatus status, Integer amount,
                                           Boolean enableUniqueAmount, Merchant merchant, String merchantOrderId,
                                           String merchantOrderStatus, RequestMethod method, String details,
                                           String bank, String callbackUrl) throws Exception {
        OrderResponse orderResponse = new OrderResponse(id, Instant.ofEpochMilli(createdAt), Instant.ofEpochMilli(expiresAt),
                clientId, internalId, status, amount, enableUniqueAmount, merchant, merchantOrderId,
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
                .andExpect(jsonPath("$.enableUniqueAmount").value(enableUniqueAmount))
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
    void getOrderShouldReturn400IfOrderNotFound(UUID id) throws Exception {
        when(orderService.findById(any(), any())).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/private/order/" + id.toString())
                .with(user("fe795642-3aba-45b2-84a0-c69c07673004").roles("ADMIN"))
                .with(csrf()))
                .andExpect(status().isBadRequest());
    }
}