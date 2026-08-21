package net.rcetech.meta.config;

import jakarta.validation.constraints.NotEmpty;
import lombok.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "processing")
@Validated
public record ProcessingConfigurationProperties(@NonNull Keycloak keycloak) {
    public record Keycloak(@NonNull Webhook webhook) {
        public record Webhook(@NotEmpty String username, @NotEmpty String password) {}
    }
}
