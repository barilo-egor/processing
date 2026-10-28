package net.rcetech.web.documentation;

import net.rcetech.meta.documentation.dto.DocumentationItem;
import net.rcetech.meta.documentation.dto.DocumentationPage;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DocumentationServiceTest {

    private static final String API_URL = "https://api.example.com";

    private final DocumentationService documentationService =
            new DocumentationService(new PathMatchingResourcePatternResolver(), API_URL);

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "OPERATOR|shift;routes;incidents;faq|Смена;Маршруты;Инциденты;faq",
            "ADMIN|settings;rights;shift;routes;incidents;faq|Настройки;Права;Смена;Маршруты;Инциденты;faq",
            "CLIENT|orders;balance;guide|Заявки;Баланс;guide"
    })
    @DisplayName("Должен вернуть сначала страницы своей роли, затем ролей ниже по иерархии и общие; внутри папки — по числовому префиксу, страницы без префикса — в конце папки.")
    void findAll_shouldReturnSortedItemsOfRole(Role role, String ids, String titles) {
        List<DocumentationItem> items = documentationService.findAll(role);

        assertAll(
                () -> assertEquals(Arrays.asList(ids.split(";")), items.stream().map(DocumentationItem::id).toList()),
                () -> assertEquals(Arrays.asList(titles.split(";")), items.stream().map(DocumentationItem::title).toList())
        );
    }

    @ParameterizedTest
    @CsvSource({
            "OPERATOR,routes,Маршруты,Текст маршрутов.",
            "OPERATOR,shift,Смена,Текст смены.",
            "ADMIN,incidents,Инциденты,Текст инцидентов.",
            "ADMIN,settings,Настройки,Текст настроек.",
            "ADMIN,shift,Смена,Текст смены.",
            "CLIENT,orders,Заявки,Про заявки.",
            "CLIENT,balance,Баланс,Про баланс."
    })
    @DisplayName("Должен вернуть страницу с заголовком из первой строки и содержимым без заголовка.")
    void find_shouldReturnPageWithTitleFromHeading(Role role, String id, String title, String content) {
        Optional<DocumentationPage> page = documentationService.find(role, id);

        assertTrue(page.isPresent());
        assertEquals(new DocumentationPage(id, title, content), page.get());
    }

    @ParameterizedTest
    @CsvSource({
            "OPERATOR,faq,Вопросы без заголовка.",
            "CLIENT,guide,Руководство без заголовка."
    })
    @DisplayName("Если в странице нет заголовка, заголовком должен стать id, а содержимым — весь текст.")
    void find_shouldUseIdAsTitleIfNoHeading(Role role, String id, String content) {
        Optional<DocumentationPage> page = documentationService.find(role, id);

        assertTrue(page.isPresent());
        assertEquals(new DocumentationPage(id, id, content), page.get());
    }

    @ParameterizedTest
    @CsvSource({
            "OPERATOR,settings",
            "OPERATOR,rights",
            "CLIENT,routes",
            "CLIENT,settings",
            "OPERATOR,orders",
            "ADMIN,orders",
            "ADMIN,unknown",
            "ADMIN,01_routes",
            "ADMIN,routes.md",
            "ADMIN,admin/settings",
            "OPERATOR,../client/guide"
    })
    @DisplayName("Для страницы, недоступной роли или несуществующей, должен вернуть пустой Optional.")
    void find_shouldReturnEmptyIfPageNotAvailable(Role role, String id) {
        assertTrue(documentationService.find(role, id).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            "support,1_guide.md,support,2_guide.md",
            "support,faq.md,support/admin,05_faq.md",
            "support/operator,limits.md,support/admin,limits.md",
            "client,2_orders.md,client,orders.md"
    })
    @DisplayName("Если у двух страниц одной документации совпадает id, должно быть выброшено исключение.")
    void constructor_shouldThrowIfDuplicateId(String firstDirectory, String firstFilename,
                                              String secondDirectory, String secondFilename) throws IOException {
        ResourcePatternResolver resolver = resolver(firstDirectory, firstFilename, secondDirectory, secondFilename);

        assertThrows(BaseException.class, () -> new DocumentationService(resolver, API_URL));
    }

    @ParameterizedTest
    @CsvSource({
            "client,faq.md,support,faq.md",
            "client,guide.md,support/admin,01_guide.md"
    })
    @DisplayName("Совпадение id у клиентской страницы и страницы поддержки должно быть допустимо.")
    void constructor_shouldAllowSameIdInClientAndSupport(String firstDirectory, String firstFilename,
                                                         String secondDirectory, String secondFilename) throws IOException {
        ResourcePatternResolver resolver = resolver(firstDirectory, firstFilename, secondDirectory, secondFilename);

        assertDoesNotThrow(() -> new DocumentationService(resolver, API_URL));
    }

    @ParameterizedTest
    @ValueSource(strings = {"support", "support/operator", "support/admin", "client"})
    @DisplayName("Каждая папка должна загружаться один раз, даже если она доступна нескольким ролям.")
    void constructor_shouldLoadEachDirectoryOnce(String directory) throws IOException {
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        when(resolver.getResources(anyString())).thenReturn(new Resource[0]);

        new DocumentationService(resolver, API_URL);

        verify(resolver, times(1)).getResources("classpath*:docs/" + directory + "/*.md");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "https://api.example.com|Заголовок {{apiUrl}}|Адрес `{{apiUrl}}/api/v1`, запрос `GET {{apiUrl}}/api/v1/order`.|Заголовок https://api.example.com|Адрес `https://api.example.com/api/v1`, запрос `GET https://api.example.com/api/v1/order`.",
            "https://gateway.rcetech.net|API|`POST {{apiUrl}}/api/v1/order`|API|`POST https://gateway.rcetech.net/api/v1/order`",
            "https://api.other.org|Страница|Текст без адреса.|Страница|Текст без адреса."
    })
    @DisplayName("Плейсхолдер {{apiUrl}} в заголовке и содержимом страницы должен быть заменён на адрес API.")
    void find_shouldReplaceApiUrlPlaceholder(String apiUrl, String heading, String body,
                                             String title, String content) throws IOException {
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        when(resolver.getResources(anyString())).thenReturn(new Resource[0]);
        when(resolver.getResources("classpath*:docs/client/*.md"))
                .thenReturn(new Resource[]{resource("01_api.md", "# " + heading + "\n" + body)});

        Optional<DocumentationPage> page = new DocumentationService(resolver, apiUrl).find(Role.CLIENT, "api");

        assertTrue(page.isPresent());
        assertEquals(new DocumentationPage("api", title, content), page.get());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("Если адрес API не задан, должно быть выброшено исключение.")
    void constructor_shouldThrowIfApiUrlNotSet(String apiUrl) {
        assertThrows(BaseException.class, () -> new DocumentationService(new PathMatchingResourcePatternResolver(), apiUrl));
    }

    private ResourcePatternResolver resolver(String firstDirectory, String firstFilename,
                                             String secondDirectory, String secondFilename) throws IOException {
        Map<String, List<Resource>> resources = new HashMap<>();
        resources.computeIfAbsent(firstDirectory, d -> new ArrayList<>()).add(resource(firstFilename));
        resources.computeIfAbsent(secondDirectory, d -> new ArrayList<>()).add(resource(secondFilename));
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        when(resolver.getResources(anyString())).thenReturn(new Resource[0]);
        for (Map.Entry<String, List<Resource>> entry : resources.entrySet()) {
            when(resolver.getResources("classpath*:docs/" + entry.getKey() + "/*.md"))
                    .thenReturn(entry.getValue().toArray(Resource[]::new));
        }
        return resolver;
    }

    private Resource resource(String filename) {
        return resource(filename, "# Заголовок");
    }

    private Resource resource(String filename, String text) {
        return new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
