package net.rcetech.web.documentation;

import net.rcetech.meta.documentation.dto.DocumentationItem;
import net.rcetech.meta.documentation.dto.DocumentationPage;
import net.rcetech.meta.user.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DocumentationService {

    private final DocumentationRegistry documentationRegistry;

    public DocumentationService(ResourcePatternResolver resourcePatternResolver,
                                @Value("${processing.uri.api-url}") String apiUrl) {
        this.documentationRegistry = new DocumentationRegistry(resourcePatternResolver, apiUrl);
    }

    public List<DocumentationItem> findAll(Role role) {
        return documentationRegistry.get(role).items();
    }

    public Optional<DocumentationPage> find(Role role, String id) {
        return documentationRegistry.get(role).find(id);
    }
}
