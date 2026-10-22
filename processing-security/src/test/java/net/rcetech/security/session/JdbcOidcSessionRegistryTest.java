package net.rcetech.security.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.client.oidc.authentication.logout.OidcLogoutToken;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionInformation;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.authentication.session.SessionFixationProtectionEvent;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JdbcOidcSessionRegistry.class)
class JdbcOidcSessionRegistryTest {

    @Container
    static MySQLContainer mySQLContainer = new MySQLContainer("mysql:8.0.46");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mySQLContainer::getUsername);
        registry.add("spring.datasource.password", mySQLContainer::getPassword);
    }

    @Autowired
    private JdbcOidcSessionRegistry registry;

    @Autowired
    private JdbcClient jdbcClient;

    @ParameterizedTest
    @CsvSource({
            "http://localhost:8081/realms/processing,keycloak-bff,user-1,sid-1,sid-2,session-a,session-b,X-XSRF-TOKEN,csrf-1",
            "https://auth.example.com/realms/prod,bff,user-2,7f1c,9e2d,8C1F3A,0B2E4D,X-CSRF,csrf-2"
    })
    @DisplayName("Метод должен удалить и вернуть сессию с sid из logout-токена, не трогая другие сессии.")
    void removeSessionInformation_shouldRemoveSessionByProviderSessionId(String issuer, String audience, String subject,
                                                                        String sid, String otherSid,
                                                                        String sessionId, String otherSessionId,
                                                                        String csrfHeader, String csrfToken) {
        registry.saveSessionInformation(information(sessionId, issuer, audience, subject, sid, Map.of(csrfHeader, csrfToken)));
        registry.saveSessionInformation(information(otherSessionId, issuer, audience, subject, otherSid, Map.of()));

        List<OidcSessionInformation> removed = toList(registry.removeSessionInformation(
                logoutToken(issuer, audience, subject, sid)));

        assertEquals(1, removed.size());
        assertEquals(sessionId, removed.getFirst().getSessionId());
        assertEquals(subject, removed.getFirst().getPrincipal().getSubject());
        assertEquals(Map.of(csrfHeader, csrfToken), removed.getFirst().getAuthorities());
        assertEquals(List.of(otherSessionId), sessionIds());
    }

    @ParameterizedTest
    @CsvSource({
            "http://localhost:8081/realms/processing,keycloak-bff,user-1,user-2,session-a,session-b,session-c",
            "https://auth.example.com/realms/prod,bff,7d0c2b9e,5a1e3f7c,8C1F3A,0B2E4D,F00D11"
    })
    @DisplayName("Метод должен удалить все сессии пользователя, если в logout-токене нет sid.")
    void removeSessionInformation_shouldRemoveAllSessionsBySubject(String issuer, String audience,
                                                                   String subject, String otherSubject,
                                                                   String sessionId1, String sessionId2,
                                                                   String otherSessionId) {
        registry.saveSessionInformation(information(sessionId1, issuer, audience, subject, "sid-1", Map.of()));
        registry.saveSessionInformation(information(sessionId2, issuer, audience, subject, "sid-2", Map.of()));
        registry.saveSessionInformation(information(otherSessionId, issuer, audience, otherSubject, "sid-3", Map.of()));

        List<String> removed = toList(registry.removeSessionInformation(logoutToken(issuer, audience, subject, null)))
                .stream()
                .map(OidcSessionInformation::getSessionId)
                .sorted()
                .toList();

        assertEquals(List.of(sessionId1, sessionId2).stream().sorted().toList(), removed);
        assertEquals(List.of(otherSessionId), sessionIds());
    }

    @ParameterizedTest
    @CsvSource({
            "http://localhost:8081/realms/processing,http://localhost:8081/realms/other,keycloak-bff,other-client,user-1,sid-1,session-a",
            "https://auth.example.com/realms/prod,https://evil.example.com/realms/prod,bff,support,5a1e3f7c,9e2d,0B2E4D"
    })
    @DisplayName("Метод не должен удалять сессию, если издатель или аудитория logout-токена не совпадают.")
    void removeSessionInformation_shouldKeepSessionIfIssuerOrAudienceDiffers(String issuer, String otherIssuer,
                                                                            String audience, String otherAudience,
                                                                            String subject, String sid,
                                                                            String sessionId) {
        registry.saveSessionInformation(information(sessionId, issuer, audience, subject, sid, Map.of()));

        List<OidcSessionInformation> byIssuer = toList(registry.removeSessionInformation(
                logoutToken(otherIssuer, audience, subject, sid)));
        List<OidcSessionInformation> byAudience = toList(registry.removeSessionInformation(
                logoutToken(issuer, otherAudience, subject, sid)));

        assertTrue(byIssuer.isEmpty());
        assertTrue(byAudience.isEmpty());
        assertEquals(List.of(sessionId), sessionIds());
    }

    @ParameterizedTest
    @CsvSource({
            "http://localhost:8081/realms/processing,keycloak-bff,user-1,sid-1,session-a,session-b",
            "https://auth.example.com/realms/prod,bff,5a1e3f7c,9e2d,8C1F3A,0B2E4D"
    })
    @DisplayName("Метод должен удалить и вернуть сессию по id сессии приложения.")
    void removeSessionInformation_shouldRemoveSessionByClientSessionId(String issuer, String audience, String subject,
                                                                      String sid, String sessionId, String otherSessionId) {
        registry.saveSessionInformation(information(sessionId, issuer, audience, subject, sid, Map.of()));
        registry.saveSessionInformation(information(otherSessionId, issuer, audience, subject, sid, Map.of()));

        OidcSessionInformation removed = registry.removeSessionInformation(sessionId);

        assertNotNull(removed);
        assertEquals(sessionId, removed.getSessionId());
        assertEquals(List.of(otherSessionId), sessionIds());
    }

    @ParameterizedTest
    @CsvSource({
            "session-a,missing-1",
            "8C1F3A,0B2E4D"
    })
    @DisplayName("Метод должен вернуть null, если сессии приложения нет в реестре.")
    void removeSessionInformation_shouldReturnNullIfSessionIsUnknown(String sessionId, String unknownSessionId) {
        registry.saveSessionInformation(information(sessionId, "http://localhost:8081/realms/processing",
                "keycloak-bff", "user-1", "sid-1", Map.of()));

        assertNull(registry.removeSessionInformation(unknownSessionId));
        assertEquals(List.of(sessionId), sessionIds());
    }

    @ParameterizedTest
    @CsvSource({
            "http://localhost:8081/realms/processing,keycloak-bff,user-1,sid-1,session-a",
            "https://auth.example.com/realms/prod,bff,5a1e3f7c,9e2d,8C1F3A"
    })
    @DisplayName("Метод должен заменить запись, если сессия приложения сохраняется повторно.")
    void saveSessionInformation_shouldReplaceExistingSession(String issuer, String audience, String subject,
                                                             String sid, String sessionId) {
        registry.saveSessionInformation(information(sessionId, issuer, audience, subject, "old-" + sid, Map.of()));
        registry.saveSessionInformation(information(sessionId, issuer, audience, subject, sid, Map.of()));

        List<OidcSessionInformation> removed = toList(registry.removeSessionInformation(
                logoutToken(issuer, audience, subject, sid)));

        assertEquals(1, removed.size());
        assertEquals(sessionId, removed.getFirst().getSessionId());
        assertEquals(List.of(), sessionIds());
    }

    @ParameterizedTest
    @CsvSource({
            "http://localhost:8081/realms/processing,keycloak-bff,user-1,sid-1,session-old,session-new",
            "https://auth.example.com/realms/prod,bff,5a1e3f7c,9e2d,8C1F3A,0B2E4D"
    })
    @DisplayName("Метод должен перенести запись на новый id сессии после защиты от фиксации сессии.")
    void onSessionIdChanged_shouldMoveSessionToNewId(String issuer, String audience, String subject, String sid,
                                                     String oldSessionId, String newSessionId) {
        registry.saveSessionInformation(information(oldSessionId, issuer, audience, subject, sid, Map.of()));

        registry.onSessionIdChanged(new SessionFixationProtectionEvent(
                new TestingAuthenticationToken(subject, null), oldSessionId, newSessionId));

        List<OidcSessionInformation> removed = toList(registry.removeSessionInformation(
                logoutToken(issuer, audience, subject, sid)));
        assertEquals(1, removed.size());
        assertEquals(newSessionId, removed.getFirst().getSessionId());
    }

    @ParameterizedTest
    @CsvSource({
            "orphan-old,orphan-new,linked-old,11,5",
            "8C1F3A,0B2E4D,F00D11,60,1"
    })
    @DisplayName("Метод должен удалить только давние записи, у которых нет сессии приложения.")
    void deleteOrphans_shouldDeleteOnlyOldRecordsWithoutSession(String orphanOld, String orphanNew, String linkedOld,
                                                                long oldMinutes, long newMinutes) {
        Instant now = Instant.now();
        saveWithRegisteredAt(orphanOld, now.minus(oldMinutes, ChronoUnit.MINUTES));
        saveWithRegisteredAt(orphanNew, now.minus(newMinutes, ChronoUnit.MINUTES));
        saveWithRegisteredAt(linkedOld, now.minus(oldMinutes, ChronoUnit.MINUTES));
        jdbcClient.sql("""
                        insert into SPRING_SESSION (PRIMARY_ID, SESSION_ID, CREATION_TIME, LAST_ACCESS_TIME,
                                                    MAX_INACTIVE_INTERVAL, EXPIRY_TIME)
                        values (?, ?, 0, 0, 1800, 0)""")
                .params("primary-" + linkedOld, linkedOld)
                .update();

        int deleted = registry.deleteOrphans(now.minus(10, ChronoUnit.MINUTES));

        assertEquals(1, deleted);
        assertEquals(List.of(orphanNew, linkedOld).stream().sorted().toList(), sessionIds());
    }

    private void saveWithRegisteredAt(String sessionId, Instant registeredAt) {
        registry.saveSessionInformation(information(sessionId, "http://localhost:8081/realms/processing",
                "keycloak-bff", "user-1", "sid-" + sessionId, Map.of()));
        jdbcClient.sql("update oidc_session set registered_at = ? where session_id = ?")
                .params(Timestamp.from(registeredAt), sessionId)
                .update();
    }

    private List<String> sessionIds() {
        return jdbcClient.sql("select session_id from oidc_session order by session_id")
                .query(String.class)
                .list();
    }

    private static OidcSessionInformation information(String sessionId, String issuer, String audience, String subject,
                                                      String sid, Map<String, String> authorities) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", issuer);
        claims.put("sub", subject);
        claims.put("aud", List.of(audience));
        claims.put("sid", sid);
        OidcIdToken idToken = new OidcIdToken("id-token-" + sessionId, Instant.now(),
                Instant.now().plusSeconds(3600), claims);
        return new OidcSessionInformation(sessionId, authorities, new DefaultOidcUser(List.of(), idToken));
    }

    private static OidcLogoutToken logoutToken(String issuer, String audience, String subject, String sid) {
        OidcLogoutToken.Builder builder = OidcLogoutToken.withTokenValue("logout-token")
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(subject)
                .issuedAt(Instant.now())
                .jti("jti")
                .events(Map.of("http://schemas.openid.net/event/backchannel-logout", Map.of()));
        if (sid != null) {
            builder.sessionId(sid);
        }
        return builder.build();
    }

    private static <T> List<T> toList(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false).toList();
    }
}
