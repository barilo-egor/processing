package net.rcetech.meta.incidents;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class IncidentTypeDictionaryFieldTest {

    private final IncidentTypeDictionaryField dictionaryField = new IncidentTypeDictionaryField();

    @Test
    @DisplayName("Метод должен вернуть название поля словаря IncidentType.")
    void getField_shouldReturnIncidentType() {
        assertEquals("IncidentType", dictionaryField.getField());
    }

    @ParameterizedTest
    @EnumSource(IncidentType.class)
    @DisplayName("Метод должен вернуть для каждого типа инцидента название, краткое описание и описание.")
    void getContent_shouldReturnNameSummaryAndDescription(IncidentType type) {
        List<Map<String, Object>> content = dictionaryField.getContent();

        Map<String, Object> actual = content.get(type.ordinal());
        assertAll(
                () -> assertEquals(IncidentType.values().length, content.size()),
                () -> assertEquals(type.name(), actual.get("name")),
                () -> assertEquals(type.getSummary(), actual.get("summary")),
                () -> assertEquals(type.getDescription(), actual.get("description"))
        );
    }
}
