package com.masiton.member.application;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.masiton.member.application.port.out.MemberDeletionJobStore;
import com.masiton.test.TestProfile;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestProfile
@Testcontainers
@DisplayName("회원 탈퇴 정리 PostgreSQL 통합")
class MemberDeletionCleanupPostgreSqlIntegrationTest {

    private static final UUID REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID FOOD_CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.10-alpine")
            .withDatabaseName("masiton")
            .withUsername("masiton")
            .withPassword("masiton_test");

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MemberDeletionJobStore jobs;

    @Autowired
    private MemberDeletionCleanupService cleanupService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("작업을 등록하고 청구한 뒤 재예약 및 완료 상태를 PostgreSQL에 반영한다")
    void 작업_등록_청구_재예약_완료_상태를_반영한다() {
        // given
        UUID memberId = UUID.randomUUID();
        Instant requestedAt = Instant.parse("2026-07-30T01:00:00Z");
        jobs.enqueue(memberId, requestedAt);
        jobs.enqueue(memberId, requestedAt.plusSeconds(1));

        // when
        List<UUID> claimed = jobs.claimDue(requestedAt, 1);

        // then
        assertThat(claimed).containsExactly(memberId);
        assertThat(count("member_deletion_job", memberId)).isEqualTo(1);
        assertThat(jobTimestamp("requested_at", memberId)).isEqualTo(requestedAt);
        assertThat(jobTimestamp("last_attempt_at", memberId)).isEqualTo(requestedAt);
        assertThat(jobTimestamp("next_attempt_at", memberId)).isEqualTo(requestedAt.plusSeconds(15 * 60));
        assertThat(jobAttemptCount(memberId)).isEqualTo(1);

        Instant rescheduledAt = Instant.parse("2026-07-30T01:05:00Z");
        jobs.reschedule(memberId, rescheduledAt);

        assertThat(jobTimestamp("next_attempt_at", memberId)).isEqualTo(rescheduledAt.plusSeconds(15 * 60));

        jobs.complete(memberId);

        assertThat(count("member_deletion_job", memberId)).isZero();
    }

    @Test
    @DisplayName("탈퇴 작업은 Action Token을 지우고 회원 및 개인화 관계를 물리 삭제한다")
    void 탈퇴_작업은_ActionToken과_개인화_관계를_물리_삭제한다() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID restaurantId = insertRestaurant();
        Instant now = Instant.now();
        insertDeletionPendingMember(memberId, now);
        insertActionToken(memberId, now);
        jdbcTemplate.update("INSERT INTO favorite (member_id, restaurant_id, favorited_at) VALUES (?, ?, ?)",
                memberId, restaurantId, asOffsetDateTime(now));
        jdbcTemplate.update("INSERT INTO recent_restaurant_view (member_id, restaurant_id, last_viewed_at) VALUES (?, ?, ?)",
                memberId, restaurantId, asOffsetDateTime(now));
        jobs.enqueue(memberId, now);

        // when
        cleanupService.run();

        // then
        assertThat(memberAccountCount(memberId)).isZero();
        assertThat(count("member_action_token", memberId)).isZero();
        assertThat(count("favorite", memberId)).isZero();
        assertThat(count("recent_restaurant_view", memberId)).isZero();
        assertThat(count("member_deletion_job", memberId)).isZero();
    }

    private UUID insertRestaurant() {
        UUID restaurantId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO restaurant (id, region_id, food_category_id, name, kakao_place_id, "
                        + "kakao_place_url, road_address, phone_number) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                restaurantId, REGION_ID, FOOD_CATEGORY_ID, "삭제 정리 테스트 식당 " + suffix,
                "cleanup-" + suffix, "https://example.com/places/" + suffix,
                "서울특별시 종로구 테스트로 1", "02-1234-5678");
        return restaurantId;
    }

    private void insertDeletionPendingMember(UUID memberId, Instant now) {
        jdbcTemplate.update("INSERT INTO member_account (id, email, password_hash, email_verified_at, status, "
                        + "deletion_requested_at, created_at, updated_at) VALUES (?, ?, ?, ?, 'DELETION_PENDING', ?, ?, ?)",
                memberId, "cleanup-" + memberId + "@example.com", "password-hash", asOffsetDateTime(now),
                asOffsetDateTime(now), asOffsetDateTime(now), asOffsetDateTime(now));
    }

    private void insertActionToken(UUID memberId, Instant now) {
        jdbcTemplate.update("INSERT INTO member_action_token (id, member_id, token_hash, purpose, status, issued_at, expires_at) "
                        + "VALUES (?, ?, ?, 'PASSWORD_RESET', 'ISSUED', ?, ?)",
                UUID.randomUUID(), memberId, new byte[32], asOffsetDateTime(now), asOffsetDateTime(now.plusSeconds(60)));
    }

    private long count(String tableName, UUID memberId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + tableName + " WHERE member_id = ?", Long.class, memberId);
    }

    private long memberAccountCount(UUID memberId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM member_account WHERE id = ?", Long.class, memberId);
    }

    private Instant jobTimestamp(String columnName, UUID memberId) {
        return jdbcTemplate.queryForObject("SELECT " + columnName + " FROM member_deletion_job WHERE member_id = ?",
                (resultSet, rowNum) -> resultSet.getTimestamp(1).toInstant(), memberId);
    }

    private int jobAttemptCount(UUID memberId) {
        return jdbcTemplate.queryForObject("SELECT attempt_count FROM member_deletion_job WHERE member_id = ?", Integer.class, memberId);
    }

    private OffsetDateTime asOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
