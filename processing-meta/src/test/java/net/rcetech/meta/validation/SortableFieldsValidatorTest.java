package net.rcetech.meta.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SortableFieldsValidatorTest {

    record Target(@SortableFields({"id", "createdAt"}) Pageable pageable) { }

    private static ValidatorFactory validatorFactory;

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "createdAt"})
    @DisplayName("Валидатор должен пропустить сортировку по разрешённому полю.")
    void isValid_shouldPassAllowedField(String field) {
        assertTrue(validator.validate(new Target(PageRequest.of(0, 10, Sort.by(field)))).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3})
    @DisplayName("Валидатор должен пропустить запрос без сортировки.")
    void isValid_shouldPassUnsorted(int page) {
        assertTrue(validator.validate(new Target(PageRequest.of(page, 10))).isEmpty());
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("Валидатор должен пропустить отсутствующую пагинацию.")
    void isValid_shouldPassNull(Pageable pageable) {
        assertTrue(validator.validate(new Target(pageable)).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            "nope,id,nope",
            "id,comment,comment",
            "createdAt,${1+1},${1+1}",
            "id,{value},{value}",
            "id,\\${1+1},\\${1+1}"
    })
    @DisplayName("Валидатор должен вернуть нарушение с именем первого неразрешённого поля.")
    void isValid_shouldRejectNotAllowedField(String first, String second, String expectedField) {
        Set<ConstraintViolation<Target>> violations =
                validator.validate(new Target(PageRequest.of(0, 10, Sort.by(first, second))));

        assertEquals(1, violations.size());
        ConstraintViolation<Target> violation = violations.iterator().next();
        assertEquals("pageable", violation.getPropertyPath().toString());
        assertEquals("Сортировка по полю «" + expectedField + "» не поддерживается.", violation.getMessage());
    }
}
