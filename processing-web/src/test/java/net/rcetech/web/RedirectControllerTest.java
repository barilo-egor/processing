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

@WebMvcTest(value = RedirectController.class, properties = "processing.uri.manage-account=http://localhost/account")
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
}
