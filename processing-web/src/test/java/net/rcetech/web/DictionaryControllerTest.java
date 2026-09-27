package net.rcetech.web;

import net.rcetech.meta.orders.OrderStatus;
import net.rcetech.meta.orders.OrderStatusDictionaryField;
import net.rcetech.meta.orders.RequestMethod;
import net.rcetech.meta.orders.RequestMethodDictionaryField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DictionaryController.class)
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

    @Test
    @DisplayName("Должен быть возвращен список полей словаря, находящихся в spring контексте.")
    void getDictionary() throws Exception {
        ResultActions resultActions = mockMvc.perform(get("/api/private/dictionary"))
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