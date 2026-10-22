package net.rcetech.meta.incidents;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class IncidentTypeTest {

    @ParameterizedTest
    @EnumSource(IncidentType.class)
    @DisplayName("У каждого типа инцидента должны быть заполнены краткое описание, описание и инструкция.")
    void shouldHaveSummaryDescriptionAndGuide(IncidentType type) {
        assertAll(
                () -> assertNotNull(type.getSummary()),
                () -> assertFalse(type.getSummary().isBlank()),
                () -> assertNotNull(type.getDescription()),
                () -> assertFalse(type.getDescription().isBlank()),
                () -> assertNotNull(type.getGuide()),
                () -> assertFalse(type.getGuide().isBlank())
        );
    }
}
