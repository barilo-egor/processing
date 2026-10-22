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

@WebMvcTest(DocumentationController.class)
@Import({MetaSecurityConfig.class})
class DocumentationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentationService documentationService;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @ParameterizedTest
    @CsvSource({
            "CLIENT,/api/support/docs",
            "CLIENT,/api/support/docs/order_route",
            "TRADER,/api/support/docs",
            "GUEST,/api/support/docs/incident"
    })
    @DisplayName("Методы должны вернуть 403, если роль пользователя не админ и не оператор.")
    void shouldReturn403IfNotAdminOrOperator(String role, String path) throws Exception {
        mockMvc.perform(get(path)
                .with(user("38c3db77-c4d7-4b21-818e-aa9731b26fe8").roles(role))
        ).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ADMIN|ADMIN|order_route|Маршруты ордеров|settings|Настройки",
            "OPERATOR|OPERATOR|faq|Вопросы|shift|Смена",
            "ADMIN;OPERATOR|ADMIN|incident|Инциденты|rights|Права",
            "ADMIN;CLIENT|ADMIN|limits|Лимиты|order_route|Маршруты ордеров",
            "OPERATOR;CLIENT|OPERATOR|shift|Смена|faq|Вопросы"
    })
    @DisplayName("Метод должен вернуть JSON списка страниц для роли админа, если она есть у пользователя, иначе для роли оператора.")
    void findAll_shouldReturnJsonOfItemsForSupportRole(String userRoles, Role expectedRole,
                                                        String firstId, String firstTitle,
                                                        String secondId, String secondTitle) throws Exception {
        when(documentationService.findAll(expectedRole)).thenReturn(List.of(
                new DocumentationItem(firstId, firstTitle), new DocumentationItem(secondId, secondTitle)
        ));

        mockMvc.perform(get("/api/support/docs")
                        .with(user("38c3db77-c4d7-4b21-818e-aa9731b26fe8").roles(userRoles.split(";"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(firstId))
                .andExpect(jsonPath("$[0].title").value(firstTitle))
                .andExpect(jsonPath("$[1].id").value(secondId))
                .andExpect(jsonPath("$[1].title").value(secondTitle));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ADMIN|ADMIN|settings|Настройки|## Общие настройки",
            "OPERATOR|OPERATOR|incident|Инциденты|Инцидент — запись о ситуации.",
            "ADMIN;OPERATOR|ADMIN|shift|Смена|## Начало смены",
            "OPERATOR;CLIENT|OPERATOR|order_route|Маршруты ордеров|## Статусы ордера"
    })
    @DisplayName("Метод должен вернуть JSON страницы для роли админа, если она есть у пользователя, иначе для роли оператора.")
    void get_shouldReturnJsonOfPageForSupportRole(String userRoles, Role expectedRole,
                                                   String id, String title, String content) throws Exception {
        when(documentationService.find(expectedRole, id))
                .thenReturn(Optional.of(new DocumentationPage(id, title, content)));

        mockMvc.perform(get("/api/support/docs/" + id)
                        .with(user("38c3db77-c4d7-4b21-818e-aa9731b26fe8").roles(userRoles.split(";"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.content").value(content));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ADMIN|ADMIN|unknown",
            "OPERATOR|OPERATOR|settings"
    })
    @DisplayName("Метод должен вернуть 404, если страница не найдена.")
    void get_shouldReturn404IfPageNotFound(String userRoles, Role expectedRole, String id) throws Exception {
        when(documentationService.find(expectedRole, id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/support/docs/" + id)
                        .with(user("38c3db77-c4d7-4b21-818e-aa9731b26fe8").roles(userRoles.split(";"))))
                .andExpect(status().isNotFound());
    }
}
