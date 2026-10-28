package net.rcetech.web.documentation;

import net.rcetech.meta.documentation.dto.DocumentationPage;
import net.rcetech.meta.user.Role;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class DocumentationRegistry {

    private final Map<DocumentationAccess, DocumentationPages> pages = new EnumMap<>(DocumentationAccess.class);

    DocumentationRegistry(ResourcePatternResolver resourcePatternResolver, String apiUrl) {
        Map<String, List<DocumentationPage>> pagesByDirectory = loadDirectories(resourcePatternResolver, apiUrl);
        for (DocumentationAccess access : DocumentationAccess.values()) {
            List<DocumentationPage> accessPages = access.getDirectories().stream()
                    .flatMap(directory -> pagesByDirectory.get(directory).stream())
                    .toList();
            pages.put(access, new DocumentationPages(accessPages));
        }
    }

    DocumentationPages get(Role role) {
        return pages.get(DocumentationAccess.of(role));
    }

    private Map<String, List<DocumentationPage>> loadDirectories(ResourcePatternResolver resourcePatternResolver,
                                                              String apiUrl) {
        DocumentationPagesLoader pagesLoader = new DocumentationPagesLoader(resourcePatternResolver, apiUrl);
        Map<String, List<DocumentationPage>> pagesByDirectory = new HashMap<>();
        for (DocumentationAccess access : DocumentationAccess.values()) {
            for (String directory : access.getDirectories()) {
                pagesByDirectory.computeIfAbsent(directory, pagesLoader::load);
            }
        }
        return pagesByDirectory;
    }
}
