package net.rcetech.web.documentation;

import net.rcetech.meta.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentationAccessTest {

    @ParameterizedTest
    @EnumSource(DocumentationAccess.class)
    @DisplayName("Для каждого доступа к документации должна существовать роль с таким же названием.")
    void shouldHaveRoleWithSameName(DocumentationAccess access) {
        assertDoesNotThrow(() -> Role.valueOf(access.name()));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("Для каждой роли должен быть определен доступ к документации с таким же названием.")
    void of_shouldReturnAccessWithSameName(Role role) {
        assertEquals(role.name(), DocumentationAccess.of(role).name());
    }
}
