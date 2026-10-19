package net.rcetech.web.documentation;

import net.rcetech.meta.WebPath;
import net.rcetech.meta.documentation.dto.DocumentationItem;
import net.rcetech.meta.documentation.dto.DocumentationPage;
import net.rcetech.meta.user.ClientAccess;
import net.rcetech.meta.user.Role;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(WebPath.CLIENT_API_PATH + "/docs")
@ClientAccess
public class ClientDocumentationController {

    private final DocumentationService documentationService;

    public ClientDocumentationController(DocumentationService documentationService) {
        this.documentationService = documentationService;
    }

    @GetMapping
    public List<DocumentationItem> findAll() {
        return documentationService.findAll(Role.CLIENT);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentationPage> get(@PathVariable String id) {
        return ResponseEntity.of(documentationService.find(Role.CLIENT, id));
    }
}
