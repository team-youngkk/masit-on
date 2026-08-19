package com.masiton.security.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.masiton.common.web.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("레거시 관리자 actor JDBC 해석기")
class JdbcLegacyAdminActorResolverTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final JdbcLegacyAdminActorResolver resolver = new JdbcLegacyAdminActorResolver(jdbcTemplate);

    @Test
    @DisplayName("활성 전환 매핑이 있으면 레거시 관리자 식별자를 반환한다")
    void resolve_활성전환매핑_레거시관리자식별자반환() {
        UUID memberAccountId = UUID.randomUUID();
        UUID legacyAdminAccountId = UUID.randomUUID();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(memberAccountId)))
                .thenReturn(List.of(legacyAdminAccountId));

        UUID result = resolver.resolve(memberAccountId);

        assertThat(result).isEqualTo(legacyAdminAccountId);
    }

    @Test
    @DisplayName("활성 전환 매핑이 없으면 접근을 거부한다")
    void resolve_활성전환매핑없음_접근거부() {
        UUID memberAccountId = UUID.randomUUID();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(memberAccountId)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> resolver.resolve(memberAccountId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo("FORBIDDEN");
    }
}
