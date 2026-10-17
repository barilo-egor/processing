package net.rcetech.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@WebMvcTest(value = RedirectController.class, properties = "processing.uri.manage-account=http://localhost/account")
class RedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "ADMIN;/support/dashboard",
            "OPERATOR;/support/dashboard",
            "ADMIN,CLIENT;/support/dashboard",
            "OPERATOR,CLIENT;/support/dashboard",
            "CLIENT;/client/dashboard",
            "'';/no-role",
            "UNKNOWN;/no-role"
    })
    @DisplayName("Пользователь должен перенаправляться на страницу, соответствующую его ролям.")
    void redirect(String roles, String expectedUrl) throws Exception {
        mockMvc.perform(get("/").with(request -> {
                    Arrays.stream(roles.split(","))
                            .filter(role -> !role.isEmpty())
                            .forEach(request::addUserRole);
                    return request;
                }))
                .andExpect(redirectedUrl(expectedUrl));
    }
}
