package net.rcetech.security.session;

import net.rcetech.domain.test.SharedMySQLContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(UserSessionRemover.class)
class UserSessionRemoverTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        SharedMySQLContainer.register(registry);
    }

    @Autowired
    private UserSessionRemover userSessionRemover;

    @Autowired
    private JdbcClient jdbcClient;

    @CsvSource({
            "6f1d2c3b-4a5e-4f60-8b7c-9d0e1f2a3b4c,1,2",
            "0a9b8c7d-6e5f-4a3b-9c1d-2e3f4a5b6c7d,3,1"
    })
    @ParameterizedTest
    @DisplayName("Метод должен удалить сессии приложения, их атрибуты и OIDC-сессии пользователя, не трогая чужие.")
    void removeAll_shouldRemoveOnlyUserSessions(String principalName, int userSessions, int otherSessions) {
        String otherPrincipalName = UUID.randomUUID().toString();
        for (int i = 0; i < userSessions; i++) {
            saveSession(principalName);
        }
        for (int i = 0; i < otherSessions; i++) {
            saveSession(otherPrincipalName);
        }

        int removed = userSessionRemover.removeAll(principalName);

        assertAll(
                () -> assertEquals(userSessions, removed),
                () -> assertEquals(List.of(otherPrincipalName), jdbcClient.sql(
                                "select distinct PRINCIPAL_NAME from SPRING_SESSION")
                        .query(String.class).list()),
                () -> assertEquals(List.of(otherPrincipalName), jdbcClient.sql(
                                "select distinct subject from oidc_session")
                        .query(String.class).list()),
                () -> assertEquals(otherSessions, jdbcClient.sql(
                                "select count(*) from SPRING_SESSION_ATTRIBUTES")
                        .query(Integer.class).single())
        );
    }

    private void saveSession(String principalName) {
        String primaryId = UUID.randomUUID().toString();
        String sessionId = UUID.randomUUID().toString();
        jdbcClient.sql("""
                        insert into SPRING_SESSION (PRIMARY_ID, SESSION_ID, CREATION_TIME, LAST_ACCESS_TIME,
                                                    MAX_INACTIVE_INTERVAL, EXPIRY_TIME, PRINCIPAL_NAME)
                        values (?, ?, 0, 0, 1800, 0, ?)""")
                .params(primaryId, sessionId, principalName)
                .update();
        jdbcClient.sql("""
                        insert into SPRING_SESSION_ATTRIBUTES (SESSION_PRIMARY_ID, ATTRIBUTE_NAME, ATTRIBUTE_BYTES)
                        values (?, 'SPRING_SECURITY_CONTEXT', ?)""")
                .params(primaryId, new byte[]{1, 2, 3})
                .update();
        jdbcClient.sql("""
                        insert into oidc_session (session_id, issuer, subject, provider_session_id, registered_at, information)
                        values (?, 'http://localhost:8081/realms/processing', ?, ?, ?, ?)""")
                .params(sessionId, principalName, "sid-" + sessionId, Timestamp.from(Instant.now()), new byte[]{4, 5})
                .update();
    }
}
