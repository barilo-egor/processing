package net.rcetech.orders.controller;

import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.exception.BadRequestException;
import net.rcetech.meta.exception.ServiceException;
import net.rcetech.meta.user.Role;
import net.rcetech.orders.callback.CallbackDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClientCallbackController.class)
@Import(MetaSecurityConfig.class)
class ClientCallbackControllerTest {

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private CallbackDispatcher callbackDispatcher;

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @CsvSource({
            "6e299aaf-4d82-438a-85bc-e2f8bcbadbfb,ADMIN",
            "05aa6142-6d22-42f3-99ff-93c9240858b5,OPERATOR"
    })
    @DisplayName("Метод должен повторно отправить КБ по ордеру и вернуть статус 200.")
    void resend_shouldResendAndReturn200(UUID orderId, Role role) throws Exception {
        mockMvc.perform(post("/api/support/client-callback/" + orderId)
                        .with(user("fe795642-3aba-45b2-84a0-c69c07673004").roles(role.name()))
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(callbackDispatcher).resend(orderId);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, mode = EnumSource.Mode.EXCLUDE, names = {"ADMIN", "OPERATOR"})
    @DisplayName("Метод должен вернуть статус 403, если запрос выполняется не админ или оператор.")
    void resend_shouldReturn403IfNotAdminOrOperator(Role role) throws Exception {
        mockMvc.perform(post("/api/support/client-callback/" + UUID.randomUUID())
                        .with(user("fe795642-3aba-45b2-84a0-c69c07673004").roles(role.name()))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(callbackDispatcher, never()).resend(any());
    }

    @ParameterizedTest
    @CsvSource({
            "6e299aaf-4d82-438a-85bc-e2f8bcbadbfb,Ордер не найден.",
            "05aa6142-6d22-42f3-99ff-93c9240858b5,Повторно отправить можно только недоставленный КБ о текущем статусе ордера."
    })
    @DisplayName("Метод должен вернуть статус 400 с описанием, если КБ нельзя отправить повторно.")
    void resend_shouldReturn400IfCannotResend(UUID orderId, String message) throws Exception {
        doThrow(new BadRequestException(message)).when(callbackDispatcher).resend(orderId);

        mockMvc.perform(post("/api/support/client-callback/" + orderId)
                        .with(user("fe795642-3aba-45b2-84a0-c69c07673004").roles(Role.ADMIN.name()))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value(message));
    }

    @ParameterizedTest
    @CsvSource({
            "6e299aaf-4d82-438a-85bc-e2f8bcbadbfb,КБ не доставлен: сервер клиента не принял запрос.",
            "05aa6142-6d22-42f3-99ff-93c9240858b5,Сервер клиента недоступен."
    })
    @DisplayName("Метод должен вернуть статус 500 с сообщением, если КБ не доставлен.")
    void resend_shouldReturn500IfNotDelivered(UUID orderId, String message) throws Exception {
        doThrow(new ServiceException(message)).when(callbackDispatcher).resend(orderId);

        mockMvc.perform(post("/api/support/client-callback/" + orderId)
                        .with(user("fe795642-3aba-45b2-84a0-c69c07673004").roles(Role.OPERATOR.name()))
                        .with(csrf()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value(message));
    }
}
