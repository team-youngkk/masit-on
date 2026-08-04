package com.masiton.common.idempotency;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.masiton.common.idempotency.application.IdempotencyActorType;
import com.masiton.common.idempotency.application.IdempotencyApiScope;
import com.masiton.common.idempotency.application.IdempotencyExecutionResult;
import com.masiton.common.idempotency.application.IdempotencyRecordCleanupService;
import com.masiton.common.idempotency.application.IdempotencyRequest;
import com.masiton.common.idempotency.application.IdempotencyResponse;
import com.masiton.common.idempotency.application.IdempotentCreationService;
import com.masiton.test.FullContextIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("멱등 생성 PostgreSQL 통합")
class IdempotencyPostgreSqlIntegrationTest extends FullContextIntegrationTest {

    @Autowired
    private IdempotentCreationService creationService;
    @Autowired
    private IdempotencyRecordCleanupService cleanupService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS idempotency_test_resource (
                    id uuid PRIMARY KEY,
                    value varchar(32) NOT NULL
                )
                """);
        jdbcTemplate.execute("TRUNCATE TABLE idempotency_record, idempotency_test_resource");
    }

    @Test
    @DisplayName("동시 최초 같은 요청은 패자 자원을 롤백하고 승자 응답 한 건으로 수렴한다")
    void 동시최초요청_같은본문_승자응답한건으로수렴한다() throws Exception {
        // given
        UUID actorId = UUID.randomUUID();
        IdempotencyRequest request = request(actorId, IdempotencyApiScope.MEMBER_COLLECTIONS, hash(1));
        CountDownLatch bothCreatedResources = new CountDownLatch(2);
        CountDownLatch releaseCreation = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<IdempotencyExecutionResult> first = executor.submit(
                    () -> creationService.execute(request,
                            () -> createResource(bothCreatedResources, releaseCreation)));
            Future<IdempotencyExecutionResult> second = executor.submit(
                    () -> creationService.execute(request,
                            () -> createResource(bothCreatedResources, releaseCreation)));

            assertThat(bothCreatedResources.await(10, TimeUnit.SECONDS)).isTrue();
            releaseCreation.countDown();

            // when
            List<IdempotencyExecutionResult> results = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));

            // then
            assertThat(results).extracting(IdempotencyExecutionResult::replayed)
                    .containsExactlyInAnyOrder(false, true);
            assertThat(results).extracting(result -> result.response().resourceId())
                    .containsOnly(results.getFirst().response().resourceId());
        }
        assertThat(count("idempotency_test_resource")).isEqualTo(1);
        assertThat(count("idempotency_record")).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 원문 키도 호출 주체와 API scope가 다르면 각각 성공 기록을 만든다")
    void 같은키_다른주체와Scope_서로격리한다() {
        // given
        String rawKey = "shared-opaque-key";
        byte[] requestHash = hash(2);
        UUID memberOne = UUID.randomUUID();
        UUID memberTwo = UUID.randomUUID();
        IdempotencyRequest firstActor = IdempotencyRequest.of(
                IdempotencyActorType.MEMBER, memberOne,
                IdempotencyApiScope.MEMBER_COLLECTIONS, rawKey, requestHash);
        IdempotencyRequest secondActor = IdempotencyRequest.of(
                IdempotencyActorType.MEMBER, memberTwo,
                IdempotencyApiScope.MEMBER_COLLECTIONS, rawKey, requestHash);
        IdempotencyRequest anotherScope = IdempotencyRequest.of(
                IdempotencyActorType.MEMBER, memberOne,
                IdempotencyApiScope.MEMBER_REPORTS, rawKey, requestHash);

        // when
        creationService.execute(firstActor, this::createResource);
        creationService.execute(secondActor, this::createResource);
        creationService.execute(anotherScope, this::createResource);

        // then
        assertThat(count("idempotency_record")).isEqualTo(3);
        assertThat(count("idempotency_test_resource")).isEqualTo(3);
    }

    @Test
    @DisplayName("만료 경계 기록은 cleanup 전에도 재생하지 않고 새 기록으로 교체한다")
    void 만료기록_현재시각이하_cleanup전에도교체한다() {
        // given
        IdempotencyRequest request = request(
                UUID.randomUUID(), IdempotencyApiScope.MEMBER_SUBMISSIONS, hash(3));
        IdempotencyExecutionResult first = creationService.execute(request, this::createResource);
        jdbcTemplate.update("""
                UPDATE idempotency_record
                   SET created_at = CURRENT_TIMESTAMP - INTERVAL '25 hours',
                       expires_at = CURRENT_TIMESTAMP
                """);

        // when
        IdempotencyExecutionResult replacement = creationService.execute(request, this::createResource);

        // then
        assertThat(replacement.replayed()).isFalse();
        assertThat(replacement.response().resourceId()).isNotEqualTo(first.response().resourceId());
        assertThat(count("idempotency_record")).isEqualTo(1);
        assertThat(count("idempotency_test_resource")).isEqualTo(2);
    }

    @Test
    @DisplayName("만료 기록 1001건은 1000건 단위 커밋으로 전부 정리되고 재실행은 0건이다")
    void 만료정리_1000건초과_배치정리후재실행에수렴한다() {
        // given
        UUID actorId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO idempotency_record (
                    id, actor_type, actor_id, api_scope, key_hash, request_hash,
                    response_status, response_body, resource_id, created_at, expires_at
                )
                SELECT md5(g::text)::uuid,
                       'MEMBER', ?, 'POST:/api/me/reports',
                       decode(md5(g::text) || md5((g + 10000)::text), 'hex'),
                       decode(md5((g + 20000)::text) || md5((g + 30000)::text), 'hex'),
                       201, jsonb_build_object('sequence', g), md5((g + 40000)::text)::uuid,
                       CURRENT_TIMESTAMP - INTERVAL '25 hours',
                       CURRENT_TIMESTAMP - INTERVAL '1 hour'
                  FROM generate_series(1, 1001) AS g
                """, actorId);

        // when
        int firstDeleted = cleanupService.cleanupExpiredRecords();
        int secondDeleted = cleanupService.cleanupExpiredRecords();

        // then
        assertThat(firstDeleted).isEqualTo(1_001);
        assertThat(secondDeleted).isZero();
        assertThat(count("idempotency_record")).isZero();
    }

    private IdempotencyResponse createResource() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO idempotency_test_resource (id, value) VALUES (?, ?)",
                id, "created");
        return new IdempotencyResponse(201, "{\"id\":\"" + id + "\"}", id);
    }

    private IdempotencyResponse createResource(
            CountDownLatch bothCreatedResources,
            CountDownLatch releaseCreation
    ) {
        IdempotencyResponse response = createResource();
        bothCreatedResources.countDown();
        await(releaseCreation);
        return response;
    }

    private IdempotencyRequest request(
            UUID actorId,
            IdempotencyApiScope scope,
            byte[] requestHash
    ) {
        return IdempotencyRequest.of(
                IdempotencyActorType.MEMBER, actorId, scope, "opaque-key", requestHash);
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private byte[] hash(int value) {
        byte[] hash = new byte[32];
        hash[0] = (byte) value;
        return hash;
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent idempotency request");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for concurrent request", exception);
        }
    }
}
