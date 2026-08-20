package com.masiton.member.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.nio.ByteBuffer;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.masiton.member.application.port.out.MemberActionMailOutboxStore;
import com.masiton.member.domain.model.MemberActionMailOutbox;
import com.masiton.member.domain.model.MemberActionPurpose;
import com.masiton.test.TestProfile;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestProfile
@Testcontainers
@DisplayName("회원 Action 메일 outbox 영속화")
class MemberActionMailOutboxPersistenceIntegrationTest extends com.masiton.test.FullContextIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:10:00Z");

    @Autowired
    private MemberAuthenticationService service;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MemberActionMailOutboxStore outbox;

    @Test
    @DisplayName("가입 요청은 Action Token과 암호화된 outbox 행을 같은 트랜잭션으로 기록한다")
    void register_ActionToken과Outbox_함께영속화() {
        String email = "outbox-" + UUID.randomUUID() + "@example.com";
        service.register(email, "correct horse battery staple", "source-" + UUID.randomUUID());

        Integer tokenCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM member_action_token token
                JOIN member_account account ON account.id = token.member_id
                WHERE account.email = ? AND token.purpose = 'EMAIL_VERIFICATION' AND token.status = 'ISSUED'
                """, Integer.class, email);
        Integer outboxCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM member_action_mail_outbox outbox
                JOIN member_action_token token ON token.id = outbox.member_action_token_id
                JOIN member_account account ON account.id = token.member_id
                WHERE account.email = ? AND outbox.status = 'PENDING'
                  AND octet_length(outbox.encrypted_token) > 16
                  AND octet_length(outbox.encryption_nonce) = 12
                  AND outbox.encryption_key_id = 'test-1'
                """, Integer.class, email);
        Integer copiedRecipientColumns = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'member_action_mail_outbox'
                  AND column_name IN ('email', 'member_id')
                """, Integer.class);

        assertThat(tokenCount).isEqualTo(1);
        assertThat(outboxCount).isEqualTo(1);
        assertThat(copiedRecipientColumns).isZero();
    }

    @Test
    @DisplayName("PENDING 행은 안전하게 claim하고 실패 시 재시도하며 성공 시 SENT로 완료한다")
    void outbox_Claim재시도_성공완료() {
        UUID memberId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        UUID outboxId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO member_account (id, email, password_hash, status, created_at, updated_at)
                VALUES (?, ?, 'password-hash', 'PENDING_VERIFICATION', ?, ?)
                """, memberId, "claimed-" + memberId + "@example.com", Timestamp.from(NOW), Timestamp.from(NOW));
        jdbcTemplate.update("""
                INSERT INTO member_action_token (id, member_id, token_hash, purpose, status, issued_at, expires_at)
                VALUES (?, ?, ?, 'EMAIL_VERIFICATION', 'ISSUED', ?, ?)
                """, tokenId, memberId, tokenHash(tokenId), Timestamp.from(NOW), Timestamp.from(NOW.plusSeconds(3600)));
        outbox.enqueue(new MemberActionMailOutbox(
                outboxId, tokenId, MemberActionPurpose.EMAIL_VERIFICATION,
                new byte[17], new byte[12], "test-1"), NOW);

        assertThat(outbox.claimDue(NOW, 50)).hasSize(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM member_action_mail_outbox WHERE id = ?", Integer.class, outboxId)).isEqualTo(1);

        outbox.reschedule(outboxId, NOW);
        Timestamp lockedUntil = jdbcTemplate.queryForObject("SELECT locked_until FROM member_action_mail_outbox WHERE id = ?",
                (resultSet, rowNum) -> resultSet.getTimestamp(1), outboxId);
        assertThat(lockedUntil).isNull();

        outbox.claimDue(NOW.plusSeconds(60), 50);
        assertThat(outbox.confirmDelivery(outboxId, NOW.plusSeconds(60))).isTrue();
        outbox.markSent(outboxId, NOW.plusSeconds(60));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM member_action_mail_outbox WHERE id = ?", String.class, outboxId)).isEqualTo("SENT");
    }

    @Test
    @DisplayName("claim 뒤 Action Token이 revoke되면 전달 확인은 취소 처리하고 false를 반환한다")
    void confirmDelivery_Claim뒤TokenRevoke_취소처리() {
        UUID memberId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        UUID outboxId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO member_account (id, email, password_hash, status, created_at, updated_at)
                VALUES (?, ?, 'password-hash', 'PENDING_VERIFICATION', ?, ?)
                """, memberId, "revoked-" + memberId + "@example.com", Timestamp.from(NOW), Timestamp.from(NOW));
        jdbcTemplate.update("""
                INSERT INTO member_action_token (id, member_id, token_hash, purpose, status, issued_at, expires_at)
                VALUES (?, ?, ?, 'EMAIL_VERIFICATION', 'ISSUED', ?, ?)
                """, tokenId, memberId, tokenHash(tokenId), Timestamp.from(NOW), Timestamp.from(NOW.plusSeconds(3600)));
        outbox.enqueue(new MemberActionMailOutbox(
                outboxId, tokenId, MemberActionPurpose.EMAIL_VERIFICATION,
                new byte[17], new byte[12], "test-1"), NOW);
        assertThat(outbox.claimDue(NOW, 50)).hasSize(1);
        jdbcTemplate.update("UPDATE member_action_token SET status = 'REVOKED', completed_at = ? WHERE id = ?",
                Timestamp.from(NOW.plusSeconds(1)), tokenId);

        assertThat(outbox.confirmDelivery(outboxId, NOW.plusSeconds(1))).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM member_action_mail_outbox WHERE id = ?", String.class, outboxId)).isEqualTo("CANCELLED");
    }

    private byte[] tokenHash(UUID tokenId) {
        return ByteBuffer.allocate(32)
                .putLong(tokenId.getMostSignificantBits())
                .putLong(tokenId.getLeastSignificantBits())
                .putLong(tokenId.getMostSignificantBits())
                .putLong(tokenId.getLeastSignificantBits())
                .array();
    }
}
