package net.rcetech.web.documentation;

import net.rcetech.meta.documentation.dto.DocumentationPage;
import net.rcetech.meta.exception.BaseException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class DocumentationPagesLoader {

    private static final String LOCATION_PATTERN = "classpath*:docs/%s/*.md";

    private static final String EXTENSION = ".md";

    private static final Pattern ORDER_PREFIX = Pattern.compile("^(\\d+)_");

    private static final String TITLE_PREFIX = "# ";

    private static final String API_URL_PLACEHOLDER = "{{apiUrl}}";

    private final ResourcePatternResolver resourcePatternResolver;

    private final String apiUrl;

    DocumentationPagesLoader(ResourcePatternResolver resourcePatternResolver, String apiUrl) {
        if (Objects.isNull(apiUrl) || apiUrl.isBlank()) {
            throw new BaseException("Property processing.uri.api-url is not set");
        }
        this.resourcePatternResolver = resourcePatternResolver;
        this.apiUrl = apiUrl;
    }

    List<DocumentationPage> load(String directory) {
        Resource[] resources;
        try {
            resources = resourcePatternResolver.getResources(LOCATION_PATTERN.formatted(directory));
        } catch (IOException e) {
            throw new BaseException("Failed to find documentation in directory " + directory, e);
        }
        return Arrays.stream(resources)
                .sorted(Comparator.comparingLong(this::getOrder).thenComparing(this::getFilename))
                .map(this::loadPage)
                .toList();
    }

    private DocumentationPage loadPage(Resource resource) {
        String filename = getFilename(resource);
        String id = ORDER_PREFIX.matcher(filename.substring(0, filename.length() - EXTENSION.length()))
                .replaceFirst("");
        String text;
        try {
            text = resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BaseException("Failed to read documentation page " + filename, e);
        }
        return parseContent(id, text.replace(API_URL_PLACEHOLDER, apiUrl));
    }

    private String getFilename(Resource resource) {
        return Objects.requireNonNull(resource.getFilename());
    }

    private long getOrder(Resource resource) {
        Matcher matcher = ORDER_PREFIX.matcher(getFilename(resource));
        return matcher.find()
                ? Long.parseLong(matcher.group(1))
                : Long.MAX_VALUE;
    }

    private DocumentationPage parseContent(String id, String text) {
        List<String> lines = text.strip().lines().toList();
        if (lines.isEmpty() || !lines.getFirst().startsWith(TITLE_PREFIX)) {
            return new DocumentationPage(id, id, text.strip());
        }
        String title = lines.getFirst().substring(TITLE_PREFIX.length()).strip();
        String content = String.join("\n", lines.subList(1, lines.size())).strip();
        return new DocumentationPage(id, title, content);
    }
}
