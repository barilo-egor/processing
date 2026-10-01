package net.rcetech.meta.user;

import java.util.Set;
import java.util.stream.Collectors;

public enum Role {
    CLIENT,
    OPERATOR,
    ADMIN;

    public static final Set<Role> SUPPORT_USERS = Set.of(OPERATOR, ADMIN);

    public static final Set<String> SUPPORT_USERS_WITH_PREFIX = Set.of(OPERATOR, ADMIN).stream()
            .map(Role::withPrefix)
            .collect(Collectors.toSet());

    public String withPrefix() {
        return "ROLE_" + this.name();
    }
}
