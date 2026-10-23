package net.rcetech.security.session;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.event.EventListener;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.client.oidc.authentication.logout.LogoutTokenClaimNames;
import org.springframework.security.oauth2.client.oidc.authentication.logout.OidcLogoutToken;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionInformation;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionRegistry;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.session.SessionFixationProtectionEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class JdbcOidcSessionRegistry implements OidcSessionRegistry {

    private static final Duration ORPHAN_TTL = Duration.ofMinutes(10);

    private final JdbcClient jdbcClient;

    private final SerializingConverter serializer = new SerializingConverter();

    private final DeserializingConverter deserializer = new DeserializingConverter();

    public JdbcOidcSessionRegistry(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public void saveSessionInformation(OidcSessionInformation info) {
        OidcUser user = info.getPrincipal();
        jdbcClient.sql("delete from oidc_session where session_id = ?")
                .param(info.getSessionId())
                .update();
        jdbcClient.sql("""
                        insert into oidc_session (session_id, issuer, subject, provider_session_id, registered_at, information)
                        values (?, ?, ?, ?, ?, ?)""")
                .params(info.getSessionId(),
                        Objects.requireNonNull(user.getIssuer()).toString(),
                        user.getSubject(),
                        user.getClaimAsString(LogoutTokenClaimNames.SID),
                        Timestamp.from(Instant.now()),
                        serializer.convert(info))
                .update();
    }

    @Override
    @Transactional
    public OidcSessionInformation removeSessionInformation(String clientSessionId) {
        List<OidcSessionInformation> found = jdbcClient.sql("select session_id, information from oidc_session where session_id = ?")
                .param(clientSessionId)
                .query((rs, rowNum) -> deserialize(rs.getString(1), rs.getBytes(2)))
                .list();
        if (found.isEmpty()) {
            return null;
        }
        delete(found);
        return found.getFirst();
    }

    @Override
    @Transactional
    public Iterable<OidcSessionInformation> removeSessionInformation(OidcLogoutToken token) {
        String issuer = token.getIssuer().toString();
        List<OidcSessionInformation> candidates;
        if (Objects.nonNull(token.getSessionId())) {
            candidates = jdbcClient.sql("select session_id, information from oidc_session where issuer = ? and provider_session_id = ?")
                    .params(issuer, token.getSessionId())
                    .query((rs, rowNum) -> deserialize(rs.getString(1), rs.getBytes(2)))
                    .list();
        } else if (Objects.nonNull(token.getSubject())) {
            candidates = jdbcClient.sql("select session_id, information from oidc_session where issuer = ? and subject = ?")
                    .params(issuer, token.getSubject())
                    .query((rs, rowNum) -> deserialize(rs.getString(1), rs.getBytes(2)))
                    .list();
        } else {
            return List.of();
        }
        List<OidcSessionInformation> matched = candidates.stream()
                .filter(info -> Objects.nonNull(info.getPrincipal().getAudience())
                        && !Collections.disjoint(token.getAudience(), info.getPrincipal().getAudience()))
                .toList();
        delete(matched);
        log.info("Back-channel logout пользователя {}: найдено сессий {}.", token.getSubject(), matched.size());
        return matched;
    }

    @EventListener
    @Transactional
    public void onSessionIdChanged(SessionFixationProtectionEvent event) {
        jdbcClient.sql("update oidc_session set session_id = ? where session_id = ?")
                .params(event.getNewSessionId(), event.getOldSessionId())
                .update();
    }

    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    @SchedulerLock(name = "JdbcOidcSessionRegistry.deleteOrphans", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void deleteOrphans() {
        deleteOrphans(Instant.now().minus(ORPHAN_TTL));
    }

    int deleteOrphans(Instant registeredBefore) {
        return jdbcClient.sql("""
                        delete o from oidc_session o
                        left join SPRING_SESSION s on s.SESSION_ID = o.session_id
                        where s.SESSION_ID is null and o.registered_at < ?""")
                .param(Timestamp.from(registeredBefore))
                .update();
    }

    private void delete(List<OidcSessionInformation> sessions) {
        for (OidcSessionInformation session : sessions) {
            jdbcClient.sql("delete from oidc_session where session_id = ?")
                    .param(session.getSessionId())
                    .update();
        }
    }

    private OidcSessionInformation deserialize(String sessionId, byte[] bytes) {
        return ((OidcSessionInformation) deserializer.convert(bytes)).withSessionId(sessionId);
    }
}
