package com.masiton.member.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.masiton.member.application.MemberAuthenticationState;
import com.masiton.member.application.MemberAuthenticationStoreUnavailableException;
import com.masiton.member.application.port.out.LoadMemberAuthenticationStatePort;

@Component
public class JdbcMemberAuthenticationStateAdapter implements LoadMemberAuthenticationStatePort {

    private static final String LOAD = """
            SELECT EXISTS(
                SELECT 1
                  FROM member_account
                 WHERE id = ?
                   AND status = 'ACTIVE'
            ) AS active,
            EXISTS(
                SELECT 1
                  FROM member_session_revocation
                 WHERE session_id = ?
                   AND expires_at > ?
            ) AS revoked
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcMemberAuthenticationStateAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public MemberAuthenticationState load(UUID memberId, UUID sessionId, Instant now) {
        try {
            return jdbcTemplate.queryForObject(
                    LOAD,
                    (resultSet, rowNum) -> new MemberAuthenticationState(
                            resultSet.getBoolean("active"),
                            resultSet.getBoolean("revoked")
                    ),
                    memberId,
                    sessionId,
                    Timestamp.from(now)
            );
        } catch (DataAccessException exception) {
            throw new MemberAuthenticationStoreUnavailableException(exception);
        }
    }
}
