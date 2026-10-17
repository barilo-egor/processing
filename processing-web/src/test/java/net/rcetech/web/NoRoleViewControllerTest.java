package net.rcetech.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;

@WebMvcTest(NoRoleViewController.class)
class NoRoleViewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Должна отдаваться страница для пользователя без ролей.")
    void view() throws Exception {
        mockMvc.perform(get("/no-role"))
                .andExpect(forwardedUrl("/no-role/index.html"));
    }
}
