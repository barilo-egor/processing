package net.rcetech.web.documentation;

import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.documentation.dto.DocumentationItem;
import net.rcetech.meta.documentation.dto.DocumentationPage;
import net.rcetech.meta.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClientDocumentationController.class)
@Import({MetaSecurityConfig.class})
class ClientDocumentationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentationService documentationService;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @ParameterizedTest
    @CsvSource({
            "ADMIN,/api/client/docs",
            "OPERATOR,/api/client/docs/orders",
            "TRADER,/api/client/docs",
            "GUEST,/api/client/docs/balance"
    })
    @DisplayName("Методы должны вернуть 403, если роль пользователя не клиент.")
    void shouldReturn403IfNotClient(String role, String path) throws Exception {
        mockMvc.perform(get(path)
                .with(user("0b8a3d4e-5f43-4c55-9d38-6b0f4bb0f0a1").roles(role))
        ).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({
            "0b8a3d4e-5f43-4c55-9d38-6b0f4bb0f0a1,CLIENT,orders,Заявки,balance,Баланс",
            "6a1f6c2e-21f8-4e1b-8f0c-0d5f3c1e9b77,CLIENT;ADMIN,api,Подключение API,withdrawal,Вывод средств"
    })
    @DisplayName("Метод должен вернуть JSON списка страниц только клиентской документации.")
    void findAll_shouldReturnJsonOfItems(String username, String userRoles, String firstId, String firstTitle,
                                         String secondId, String secondTitle) throws Exception {
        when(documentationService.findAll(Role.CLIENT)).thenReturn(List.of(
                new DocumentationItem(firstId, firstTitle), new DocumentationItem(secondId, secondTitle)
        ));

        mockMvc.perform(get("/api/client/docs")
                        .with(user(username).roles(userRoles.split(";"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(firstId))
                .andExpect(jsonPath("$[0].title").value(firstTitle))
                .andExpect(jsonPath("$[1].id").value(secondId))
                .andExpect(jsonPath("$[1].title").value(secondTitle));
    }

    @ParameterizedTest
    @CsvSource({
            "0b8a3d4e-5f43-4c55-9d38-6b0f4bb0f0a1,CLIENT,orders,Заявки,Как создать заявку.",
            "6a1f6c2e-21f8-4e1b-8f0c-0d5f3c1e9b77,CLIENT;OPERATOR,balance,Баланс,## Пополнение баланса"
    })
    @DisplayName("Метод должен вернуть JSON страницы только клиентской документации.")
    void get_shouldReturnJsonOfPage(String username, String userRoles, String id, String title, String content) throws Exception {
        when(documentationService.find(Role.CLIENT, id))
                .thenReturn(Optional.of(new DocumentationPage(id, title, content)));

        mockMvc.perform(get("/api/client/docs/" + id)
                        .with(user(username).roles(userRoles.split(";"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.content").value(content));
    }

    @ParameterizedTest
    @CsvSource({
            "0b8a3d4e-5f43-4c55-9d38-6b0f4bb0f0a1,CLIENT,unknown",
            "6a1f6c2e-21f8-4e1b-8f0c-0d5f3c1e9b77,CLIENT;ADMIN,order_route"
    })
    @DisplayName("Метод должен вернуть 404, если страница не найдена.")
    void get_shouldReturn404IfPageNotFound(String username, String userRoles, String id) throws Exception {
        when(documentationService.find(Role.CLIENT, id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/client/docs/" + id)
                        .with(user(username).roles(userRoles.split(";"))))
                .andExpect(status().isNotFound());
    }
}
