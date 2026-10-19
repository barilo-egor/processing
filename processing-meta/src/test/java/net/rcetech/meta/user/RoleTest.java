package net.rcetech.meta.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoleTest {

    private static List<Role> roles(String roles) {
        return Arrays.stream(roles.split(";"))
                .map(Role::valueOf)
                .toList();
    }

    @ParameterizedTest
    @CsvSource({
            "CLIENT;OPERATOR;ADMIN,ADMIN;OPERATOR;CLIENT",
            "OPERATOR;ADMIN,ADMIN;OPERATOR",
            "CLIENT;ADMIN,ADMIN;CLIENT",
            "CLIENT;OPERATOR,OPERATOR;CLIENT",
            "ADMIN,ADMIN"
    })
    @DisplayName("Компаратор должен сортировать роли по иерархии: сначала самая старшая.")
    void hierarchyComparator_shouldSortByHierarchy(String roles, String expected) {
        assertEquals(roles(expected), roles(roles).stream().sorted(Role.HIERARCHY_COMPARATOR).toList());
    }

    @Test
    @DisplayName("Компаратор должен знать все роли.")
    void hierarchyComparator_shouldContainAllRoles() {
        assertEquals(
                List.of(Role.ADMIN, Role.OPERATOR, Role.CLIENT),
                Arrays.stream(Role.values()).sorted(Role.HIERARCHY_COMPARATOR).toList()
        );
    }
}
