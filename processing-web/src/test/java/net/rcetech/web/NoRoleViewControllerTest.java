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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;

@WebMvcTest(NoRoleViewController.class)
@Import({MetaSecurityConfig.class})
class NoRoleViewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "38c3db77-c4d7-4b21-818e-aa9731b26fe8;''",
            "0b8a3d4e-5f43-4c55-9d38-6b0f4bb0f0a1;UNKNOWN"
    })
    @DisplayName("Должна отдаваться страница для пользователя без ролей.")
    void view(String username, String roles) throws Exception {
        String[] userRoles = Arrays.stream(roles.split(","))
                .filter(role -> !role.isEmpty())
                .toArray(String[]::new);
        mockMvc.perform(get("/no-role")
                        .with(user(username).roles(userRoles)))
                .andExpect(forwardedUrl("/no-role/index.html"));
    }
}
