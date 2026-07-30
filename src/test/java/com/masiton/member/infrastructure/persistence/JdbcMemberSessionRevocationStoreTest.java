package com.masiton.member.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.masiton.member.application.MemberSessionRevocation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("JdbcMemberSessionRevocationStore")
class JdbcMemberSessionRevocationStoreTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:10:00Z");

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Test
    @DisplayName("PostgreSQL upsert 전에 Redis 복구 큐에 넣고 성공 뒤 제거한다")
    void record_PostgreSqlUpsert성공_큐선적재후제거() {
        MemberSessionRevocation revocation = revocation();
        JdbcMemberSessionRevocationStore store = store();

        store.record(revocation);

        verify(jdbcTemplate).update(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("PostgreSQL upsert 장애면 Redis 복구 큐를 제거하지 않는다")
    void record_PostgreSqlUpsert실패_큐보존() {
        MemberSessionRevocation revocation = revocation();
        doThrow(new IllegalStateException("database unavailable"))
                .when(jdbcTemplate).update(anyString(), any(), any(), any());
        JdbcMemberSessionRevocationStore store = store();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> store.record(revocation))
                .isInstanceOf(IllegalStateException.class);

    }

    private JdbcMemberSessionRevocationStore store() {
        return new JdbcMemberSessionRevocationStore(jdbcTemplate);
    }

    private MemberSessionRevocation revocation() {
        return new MemberSessionRevocation(UUID.randomUUID(), NOW, NOW.plusSeconds(60));
    }
}
