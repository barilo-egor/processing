package net.rcetech.web.documentation;

import net.rcetech.meta.documentation.dto.DocumentationItem;
import net.rcetech.meta.documentation.dto.DocumentationPage;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    private final DocumentationService documentationService =
            new DocumentationService(new PathMatchingResourcePatternResolver());

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

        assertThrows(BaseException.class, () -> new DocumentationService(resolver));
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

        assertDoesNotThrow(() -> new DocumentationService(resolver));
    }

    @ParameterizedTest
    @ValueSource(strings = {"support", "support/operator", "support/admin", "client"})
    @DisplayName("Каждая папка должна загружаться один раз, даже если она доступна нескольким ролям.")
    void constructor_shouldLoadEachDirectoryOnce(String directory) throws IOException {
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        when(resolver.getResources(anyString())).thenReturn(new Resource[0]);

        new DocumentationService(resolver);

        verify(resolver, times(1)).getResources("classpath*:docs/" + directory + "/*.md");
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
        return new ByteArrayResource("# Заголовок".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
