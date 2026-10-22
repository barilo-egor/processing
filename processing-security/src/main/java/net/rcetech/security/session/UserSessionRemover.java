package net.rcetech.security.session;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UserSessionRemover {

    private final JdbcClient jdbcClient;

    public UserSessionRemover(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public int removeAll(String principalName) {
        jdbcClient.sql("delete from oidc_session where subject = ?")
                .param(principalName)
                .update();
        return jdbcClient.sql("delete from SPRING_SESSION where PRINCIPAL_NAME = ?")
                .param(principalName)
                .update();
    }
}
