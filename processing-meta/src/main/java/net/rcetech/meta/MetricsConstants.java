package net.rcetech.meta;

import lombok.experimental.UtilityClass;

@UtilityClass
public class MetricsConstants {

    /**
     * Количество ошибок при обработке ивентов от keycloak.
     */
    public static final String KEYCLOAK_EVENT_HANDLE_ERROR = "keycloak_event_handle_error";

    @UtilityClass
    public static class Tags {
        public static final String TYPE = "type";
    }
}
