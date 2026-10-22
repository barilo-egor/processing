package net.rcetech.meta.incidents;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum IncidentStatus {
    NEW("Новый"),
    RESOLVED("Обработан");

    private final String description;
}
