package net.rcetech.clients.event;

import java.util.UUID;

public record KeycloakEvent(
        EventType type,
        UUID realmId,
        UUID id,
        Long time,
        String clientId,
        UUID userId,
        String ipAddress,
        Details details
) {
    public record Details(
            String authMethod,
            Boolean signatureRequired,
            String username
    ) {
    }

    public enum EventType {
        // Аутентификация и сессии
        LOGIN,
        LOGIN_ERROR,
        LOGOUT,
        LOGOUT_ERROR,
        CODE_TO_TOKEN,
        CODE_TO_TOKEN_ERROR,
        REFRESH_TOKEN,
        REFRESH_TOKEN_ERROR,

        // Профиль и пользователи
        REGISTER,
        REGISTER_ERROR,
        UPDATE_PROFILE,
        UPDATE_PROFILE_ERROR,
        UPDATE_PASSWORD,
        UPDATE_PASSWORD_ERROR,
        UPDATE_TOTP,
        UPDATE_TOTP_ERROR,
        DELETE_ACCOUNT,
        DELETE_ACCOUNT_ERROR,

        // Запросы данных и OIDC
        USER_INFO_REQUEST, // Это ваш случай
        USER_INFO_REQUEST_ERROR,
        INTROSPECT_TOKEN,
        INTROSPECT_TOKEN_ERROR,

        // Сброс и верификация
        RESET_PASSWORD,
        RESET_PASSWORD_ERROR,
        VERIFY_EMAIL,
        VERIFY_EMAIL_ERROR,
        VERIFY_PROFILE,
        VERIFY_PROFILE_ERROR,

        // Внешние Identity Providers (IdP)
        IDENTITY_PROVIDER_LOGIN,
        IDENTITY_PROVIDER_LOGIN_ERROR,
        IDENTITY_PROVIDER_LINK_ACCOUNT,
        IDENTITY_PROVIDER_LINK_ACCOUNT_ERROR,

        // Клиенты
        CLIENT_LOGIN,
        CLIENT_LOGIN_ERROR,
        CLIENT_LOGOUT,
        CLIENT_LOGOUT_ERROR,

        // Дополнительные кастомные/редкие статусы
        REMOVE_TOTP,
        REMOVE_TOTP_ERROR,
        GRANT_CONSENT,
        GRANT_CONSENT_ERROR,
        REVOKE_CONSENT,
        REVOKE_CONSENT_ERROR,
        CUSTOM_REQUIRED_ACTION,
        CUSTOM_REQUIRED_ACTION_ERROR
    }

}
