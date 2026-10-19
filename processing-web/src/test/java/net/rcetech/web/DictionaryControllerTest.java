package net.rcetech.web;

import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusDictionaryField;
import net.rcetech.meta.orders.RequestMethod;
import net.rcetech.meta.orders.RequestMethodDictionaryField;
import net.rcetech.meta.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DictionaryController.class)
@Import({MetaSecurityConfig.class})
class DictionaryControllerTest {

    @TestConfiguration
    static class Configuration {

        @Bean
        public OrderStatusDictionaryField dictionaryField() {
            return new OrderStatusDictionaryField();
        }

        @Bean
        public RequestMethodDictionaryField methodDictionaryField() {
            return new RequestMethodDictionaryField();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("Должен быть возвращен список полей словаря, находящихся в spring контексте.")
    void getDictionary(Role role) throws Exception {
        ResultActions resultActions = mockMvc.perform(get("/api/common/dictionary")
                        .with(user("38c3db77-c4d7-4b21-818e-aa9731b26fe8").roles(role.name())))
                .andExpect(status().isOk());
        resultActions
                .andExpect(jsonPath("$.OrderStatus").isArray())
                .andExpect(jsonPath("$.OrderStatus").isNotEmpty());
        int i = 0;
        for (OrderStatus orderStatus : OrderStatus.values()) {
            resultActions
                    .andExpect(jsonPath("$.OrderStatus[" + i + "].name").value(orderStatus.name()))
                    .andExpect(jsonPath("$.OrderStatus[" + i + "].description").value(orderStatus.getDescription()));
            i++;
        }
        resultActions
                .andExpect(jsonPath("$.RequestMethod").isArray())
                .andExpect(jsonPath("$.RequestMethod").isNotEmpty());
        i = 0;
        for (RequestMethod requestMethod : RequestMethod.values()) {
            resultActions
                    .andExpect(jsonPath("$.RequestMethod[" + i + "].name").value(requestMethod.name()))
                    .andExpect(jsonPath("$.RequestMethod[" + i + "].description").value(requestMethod.getDescription()));
            i++;
        }
    }
}