package net.rcetech.meta.incidents.dto;

import jakarta.validation.constraints.Size;

public record UpdateIncidentRequest(
        @Size(max = 1000, message = "должно содержать не более 1000 символов") String comment
) {}
