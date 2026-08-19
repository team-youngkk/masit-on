package com.masiton.security.infrastructure.persistence;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.masiton.common.security.LegacyAdminActorResolver;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;

/**
 * Keeps legacy admin-account foreign keys usable while write tables are migrated.
 */
@Component
public class JdbcLegacyAdminActorResolver implements LegacyAdminActorResolver {

    private static final String FIND_LEGACY_ADMIN_ACCOUNT_ID = """
            SELECT admin_account_id
            FROM admin_account_migration_map
            WHERE member_account_id = ?
              AND migration_disposition = 'MIGRATE_ACTIVE'
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcLegacyAdminActorResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UUID resolve(UUID memberAccountId) {
        return jdbcTemplate.query(FIND_LEGACY_ADMIN_ACCOUNT_ID,
                        (resultSet, rowNum) -> resultSet.getObject("admin_account_id", UUID.class),
                        memberAccountId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }
}
