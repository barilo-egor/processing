package net.rcetech.web;

import net.rcetech.meta.config.MetaSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = RedirectController.class, properties = {
        "processing.uri.manage-account=http://localhost/account",
        "processing.uri.manage-users=http://localhost/admin/processing/console/"
})
@Import({MetaSecurityConfig.class})
class RedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

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
        String[] userRoles = Arrays.stream(roles.split(","))
                .filter(role -> !role.isEmpty())
                .toArray(String[]::new);
        mockMvc.perform(get("/")
                        .with(user("38c3db77-c4d7-4b21-818e-aa9731b26fe8").roles(userRoles)))
                .andExpect(redirectedUrl(expectedUrl));
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "ADMIN;38c3db77-c4d7-4b21-818e-aa9731b26fe8",
            "ADMIN,CLIENT;5b0f3a52-9d1e-4c61-a7f3-2e8c4d9b1a07"
    })
    @DisplayName("Администратор должен перенаправляться в консоль управления пользователями.")
    void redirectToUsers_admin(String roles, String username) throws Exception {
        mockMvc.perform(get("/manage-users")
                        .with(user(username).roles(roles.split(","))))
                .andExpect(redirectedUrl("http://localhost/admin/processing/console/"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "OPERATOR;38c3db77-c4d7-4b21-818e-aa9731b26fe8",
            "CLIENT;5b0f3a52-9d1e-4c61-a7f3-2e8c4d9b1a07",
            "OPERATOR,CLIENT;9e4d2c18-7b3a-4f05-b6d1-0c8a5e3f7d29"
    })
    @DisplayName("Пользователь без роли ADMIN не должен получать доступ к консоли управления пользователями.")
    void redirectToUsers_notAdmin(String roles, String username) throws Exception {
        mockMvc.perform(get("/manage-users")
                        .with(user(username).roles(roles.split(","))))
                .andExpect(status().isForbidden());
    }
}
