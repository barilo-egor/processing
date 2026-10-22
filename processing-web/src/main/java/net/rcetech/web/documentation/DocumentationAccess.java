package net.rcetech.web.documentation;

import lombok.Getter;
import net.rcetech.meta.user.Role;

import java.util.List;

@Getter
enum DocumentationAccess {
    CLIENT(List.of("client")),
    OPERATOR(List.of("support/operator", "support")),
    ADMIN(List.of("support/admin", "support/operator", "support"));

    private final List<String> directories;

    DocumentationAccess(List<String> directories) {
        this.directories = directories;
    }

    static DocumentationAccess of(Role role) {
        return valueOf(role.name());
    }
}
