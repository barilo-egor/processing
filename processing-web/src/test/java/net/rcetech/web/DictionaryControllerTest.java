package net.rcetech.web;

import net.rcetech.meta.DictionaryField;
import net.rcetech.meta.clients.ClientStatus;
import net.rcetech.meta.clients.ClientStatusDictionaryField;
import net.rcetech.meta.orders.RequestMethod;
import net.rcetech.meta.orders.RequestMethodDictionaryField;
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
        public DictionaryField dictionaryField() {
            return new ClientStatusDictionaryField();
        }

        @Bean
        public RequestMethodDictionaryField methodDictionaryField() {
            return new RequestMethodDictionaryField();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getDictionary() throws Exception {
        ResultActions resultActions = mockMvc.perform(get("/api/private/dictionary"))
                .andExpect(status().isOk());
        resultActions
                .andExpect(jsonPath("$.ClientStatus").isArray())
                .andExpect(jsonPath("$.ClientStatus").isNotEmpty());
        int i = 0;
        for (ClientStatus clientStatus : ClientStatus.values()) {
            resultActions
                    .andExpect(jsonPath("$.ClientStatus[" + i + "].name").value(clientStatus.name()))
                    .andExpect(jsonPath("$.ClientStatus[" + i + "].description").value(clientStatus.getDescription()));
            i++;
        }
        i = 0;
        for (RequestMethod requestMethod : RequestMethod.values()) {
            resultActions
                    .andExpect(jsonPath("$.RequestMethod[" + i + "].name").value(requestMethod.name()))
                    .andExpect(jsonPath("$.RequestMethod[" + i + "].description").value(requestMethod.getDescription()));
            i++;
        }
    }
}