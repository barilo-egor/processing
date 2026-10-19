package net.rcetech.web.documentation;

import net.rcetech.meta.WebPath;
import net.rcetech.meta.documentation.dto.DocumentationItem;
import net.rcetech.meta.documentation.dto.DocumentationPage;
import net.rcetech.meta.exception.BaseException;
import net.rcetech.meta.user.Role;
import net.rcetech.meta.user.SupportAccess;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping(WebPath.SUPPORT_API_PATH + "/docs")
@SupportAccess
public class DocumentationController {

    private final DocumentationService documentationService;

    public DocumentationController(DocumentationService documentationService) {
        this.documentationService = documentationService;
    }

    @GetMapping
    public List<DocumentationItem> findAll(Authentication authentication) {
        return documentationService.findAll(role(authentication));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentationPage> get(@PathVariable String id, Authentication authentication) {
        return ResponseEntity.of(documentationService.find(role(authentication), id));
    }

    private Role role(Authentication authentication) {
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return Role.SUPPORT_USERS.stream()
                .filter(role -> authorities.contains(role.withPrefix()))
                .sorted(Role.HIERARCHY_COMPARATOR)
                .findFirst()
                .orElseThrow(() -> new BaseException("Support role not found."));
    }
}
