package com.masiton.orchestration.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.masiton.orchestration.application.port.in.AutoRegisterVerifiedContentUseCase;
import com.masiton.orchestration.application.port.in.AutoRegisterVerifiedContentUseCase.RegistrationResult;
import com.masiton.orchestration.application.port.in.AutoRegisterVerifiedContentUseCase.VerifiedContentCommand;
import com.masiton.orchestration.application.port.out.AiRegisteredContentStore;
import com.masiton.test.FullContextIntegrationTest;
import com.masiton.ai.application.RegistrationUnitPostRegistrationCommitService;
import com.masiton.ai.application.port.out.AiRegistrationUnitStore;

@DisplayName("기존 맛집·방문 재사용 PostgreSQL 검증")
class AutoRegisterRestaurantReusePostgreSqlIntegrationTest extends FullContextIntegrationTest {

    private static final UUID REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");

    @Autowired
    private AutoRegisterVerifiedContentUseCase registration;
    @Autowired
    private AiRegisteredContentStore rollbackStore;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RegistrationUnitPostRegistrationCommitService postCommit;
    @Autowired
    private AiRegistrationUnitStore unitStore;

    @Test
    @DisplayName("ROLLBACK 감사 FK 실패는 공개 자원과 등록 단위 변경을 모두 복원한다")
    void rollback_감사실패_부분반영0건() {
        // Given
        RegistrationResult registered = registration.register(command("place", "channel", "video"));
        var unit = registeredUnit(registered);
        // When / Then: 없는 관리자 FK로 실제 감사 INSERT를 실패시킨다.
        assertThatThrownBy(() -> postCommit.rollback(unit, "회귀 검증", reverted(registered), UUID.randomUUID()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(unitStore.findBySnapshotId(unit.snapshotId()).get(0).reviewStatus()).isEqualTo("AUTO_CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT publication_status FROM restaurant WHERE id = ?",
                String.class, registered.restaurantId())).isEqualTo("PUBLIC");
        assertThat(jdbc.queryForObject("SELECT publication_status FROM visit WHERE id = ?",
                String.class, registered.visitId())).isEqualTo("PUBLIC");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_registration_unit_review", Integer.class)).isZero();
    }

    @Test
    @DisplayName("카테고리 보정 감사 실패는 단위 판정과 맛집 카테고리를 모두 복원한다")
    void adjustCategory_감사실패_부분반영0건() {
        // Given
        RegistrationResult registered = registration.register(command("place", "channel", "video"));
        var unit = registeredUnit(registered);
        UUID category = UUID.fromString("20000000-0000-4000-8000-000000000002");
        // When / Then
        assertThatThrownBy(() -> postCommit.adjustCategory(unit, category, "{}", "회귀 검증", UUID.randomUUID()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(unitStore.findBySnapshotId(unit.snapshotId()).get(0).reviewStatus()).isEqualTo("AUTO_CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT food_category_id FROM restaurant WHERE id = ?",
                UUID.class, registered.restaurantId())).isEqualTo(CATEGORY_ID);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_registration_unit_review", Integer.class)).isZero();
    }

    @Test
    @DisplayName("ROLLBACK과 카테고리 보정 동시 요청은 하나만 커밋하고 패자의 효과를 남기지 않는다")
    void review_동시롤백카테고리보정_하나의감사와일관된공개상태() throws Exception {
        // Given
        RegistrationResult registered = registration.register(command("place", "channel", "video"));
        var unit = registeredUnit(registered);
        UUID admin = admin();
        UUID category = UUID.fromString("20000000-0000-4000-8000-000000000002");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        // When
        try (var executor = Executors.newFixedThreadPool(2)) {
            var rollback = executor.submit(() -> {
                awaitStart(ready, start);
                return postCommit.rollback(unit, "동시 검증", reverted(registered), admin);
            });
            var adjust = executor.submit(() -> {
                awaitStart(ready, start);
                return postCommit.adjustCategory(unit, category, "{}", "동시 검증", admin);
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            boolean rolledBack = rollback.get(20, TimeUnit.SECONDS);
            boolean adjusted = adjust.get(20, TimeUnit.SECONDS);
            // Then
            assertThat(rolledBack ^ adjusted).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_registration_unit_review", Integer.class))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT publication_status FROM restaurant WHERE id = ?",
                    String.class, registered.restaurantId())).isEqualTo(rolledBack ? "PRIVATE" : "PUBLIC");
            assertThat(jdbc.queryForObject("SELECT food_category_id FROM restaurant WHERE id = ?",
                    UUID.class, registered.restaurantId())).isEqualTo(adjusted ? category : CATEGORY_ID);
            assertThat(unitStore.findBySnapshotId(unit.snapshotId()).get(0).registeredRestaurantId())
                    .isEqualTo(rolledBack ? null : registered.restaurantId());
        }
    }

    @Test
    @DisplayName("CAS 후 같은 Snapshot의 단일 다른 등록 단위 참조도 공유 자원으로 보존한다")
    void rollback_같은스냅샷다른단위참조_공개자원보존() {
        // Given
        RegistrationResult registered = registration.register(command("place", "channel", "video"));
        var unit = registeredUnit(registered);
        unitStore.insert(new AiRegistrationUnitStore.RegistrationUnitInsert(unit.snapshotId(), 2, "고정 식당",
                "AUTO_CONFIRMED", null, "{}", "{}", registered.restaurantId(), registered.creatorId(),
                registered.videoId(), registered.visitId(), "[\"restaurant\",\"creator\",\"video\",\"visit\"]",
                "WORKER", OffsetDateTime.now()));
        // When
        assertThat(postCommit.rollback(unit, "공유 검증", reverted(registered), admin())).isTrue();
        // Then
        for (String table : java.util.List.of("restaurant", "creator", "video", "visit")) {
            assertThat(jdbc.queryForObject("SELECT publication_status FROM " + table, String.class))
                    .isEqualTo("PUBLIC");
        }
    }

    @Test
    @DisplayName("CAS 후 같은 Snapshot의 작업 등록 참조가 있으면 자원과 태그를 공개 상태로 보존한다")
    void rollback_같은스냅샷작업등록참조_공개자원보존() {
        // Given: 작업 단위와 작업 수준 등록 결과가 동일한 자원을 가리키는 상태
        RegistrationResult registered = registration.register(command("place", "channel", "video"));
        var unit = registeredUnit(registered);
        jdbc.update("""
                UPDATE ai_candidate_snapshot SET registered_restaurant_id = ?, restaurant_created = true,
                    registered_creator_id = ?, creator_created = true, registered_video_id = ?, video_created = true,
                    registered_visit_id = ?, visit_created = true WHERE id = ?
                """, registered.restaurantId(), registered.creatorId(), registered.videoId(), registered.visitId(),
                unit.snapshotId());
        // When: 등록 단위 참조는 롤백 CAS에서 제거된 뒤 공개 상태 보존 판정을 한다
        assertThat(postCommit.rollback(unit, "작업 참조 보존", reverted(registered), admin())).isTrue();
        // Then: 작업 Snapshot 참조가 남아 있으므로 네 자원 모두 계속 공개된다
        assertThat(jdbc.queryForObject("SELECT count(*) FROM visit WHERE id = ? AND publication_status = 'PUBLIC'",
                Integer.class, registered.visitId())).isEqualTo(1);
        for (String table : java.util.List.of("restaurant", "creator", "video")) {
            UUID id = switch (table) {
                case "restaurant" -> registered.restaurantId();
                case "creator" -> registered.creatorId();
                default -> registered.videoId();
            };
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table
                    + " WHERE id = ? AND publication_status = 'PUBLIC'", Integer.class, id)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("전체 작업 롤백은 다른 Job Snapshot이 참조하는 자원을 공개 상태로 보존한다")
    void 전체작업롤백_다른작업스냅샷참조_공개자원보존() {
        // Given: 현재 등록 단위 외에 다른 Job의 Snapshot이 같은 자원을 참조한다
        RegistrationResult registered = registration.register(command("place", "channel", "video"));
        var unit = registeredUnit(registered);
        UUID otherJobId = UUID.randomUUID();
        UUID otherSnapshotId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_extraction_job (id, source, priority, youtube_channel_id, youtube_video_id,
                    video_url, input_mode, input_hash, provider, model_version, prompt_version, schema_version,
                    execution_status, attempt_count, created_at)
                VALUES (?, 'ADMIN', 'REALTIME', 'other-channel', 'other-video',
                    'https://www.youtube.com/watch?v=other-video', 'ADMIN_TEXT', ?, 'GOOGLE_GEMINI',
                    'gemini-3.5-flash-lite', 'P8', 'S2', 'QUEUED', 0, CURRENT_TIMESTAMP)
                """, otherJobId, com.masiton.test.IntegrationTestFixtures.sha256(otherJobId.toString()));
        jdbc.update("""
                INSERT INTO ai_candidate_snapshot (id, job_id, snapshot_version, candidate_fields, candidate_tags,
                    field_confidences, evidence, missing_fields, review_status, registered_restaurant_id,
                    reviewed_at, restaurant_created, registered_creator_id, creator_created, registered_video_id,
                    video_created, registered_visit_id, visit_created, created_at)
                VALUES (?, ?, 1, '{}', '[]', '{}', '{}', '[]', 'AUTO_CONFIRMED', ?, CURRENT_TIMESTAMP, true, ?, true,
                    ?, true, ?, true, CURRENT_TIMESTAMP)
                """, otherSnapshotId, otherJobId, registered.restaurantId(), registered.creatorId(),
                registered.videoId(), registered.visitId());

        // When: 전체 작업 롤백 경로는 등록 단위 식별자 없이 공유 참조를 판정한다
        rollbackStore.makePrivateIfCreated(unit.snapshotId(), registered.restaurantId(), true,
                registered.creatorId(), true, registered.videoId(), true, registered.visitId(), true);

        // Then: 외부 Job의 참조가 살아 있는 동안 네 자원을 공개 상태로 유지한다
        for (String table : java.util.List.of("restaurant", "creator", "video", "visit")) {
            assertThat(jdbc.queryForObject("SELECT publication_status FROM " + table + " WHERE id = ?",
                    String.class, resourceId(table, registered))).isEqualTo("PUBLIC");
        }
    }

    private UUID resourceId(String table, RegistrationResult registered) {
        return switch (table) {
            case "restaurant" -> registered.restaurantId();
            case "creator" -> registered.creatorId();
            case "video" -> registered.videoId();
            case "visit" -> registered.visitId();
            default -> throw new IllegalArgumentException("지원하지 않는 등록 자원");
        };
    }

    private void awaitStart(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 요청 시작 실패");
        }
    }

    private UUID admin() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO member_account(id,email,password_hash,role) VALUES (?,?,'test-hash','ADMIN')",
                id, id + "@example.test");
        return id;
    }

    private String reverted(RegistrationResult registered) {
        return "{\"restaurantId\":\"%s\",\"creatorId\":\"%s\",\"videoId\":\"%s\",\"visitId\":\"%s\"}"
                .formatted(registered.restaurantId(), registered.creatorId(), registered.videoId(), registered.visitId());
    }

    private AiRegistrationUnitStore.RegistrationUnitRow registeredUnit(RegistrationResult result) {
        UUID jobId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        var reusedResources = new ArrayList<String>();
        if (!result.restaurantCreated()) {
            reusedResources.add("restaurant");
        }
        if (!result.creatorCreated()) {
            reusedResources.add("creator");
        }
        if (!result.videoCreated()) {
            reusedResources.add("video");
        }
        if (!result.visitCreated()) {
            reusedResources.add("visit");
        }
        String reusedResourcesJson = reusedResources.stream()
                .map(resource -> "\"" + resource + "\"")
                .collect(Collectors.joining(",", "[", "]"));
        jdbc.update("""
                INSERT INTO ai_extraction_job (id, source, priority, youtube_channel_id, youtube_video_id,
                    video_url, input_mode, input_hash, provider, model_version, prompt_version, schema_version,
                    execution_status, attempt_count, created_at)
                VALUES (?, 'ADMIN', 'REALTIME', 'channel', 'video', 'https://www.youtube.com/watch?v=video',
                    'ADMIN_TEXT', ?, 'GOOGLE_GEMINI', 'gemini-3.5-flash-lite', 'P8', 'S2', 'QUEUED', 0, CURRENT_TIMESTAMP)
                """, jobId, com.masiton.test.IntegrationTestFixtures.sha256(jobId.toString()));
        jdbc.update("""
                INSERT INTO ai_candidate_snapshot (id, job_id, snapshot_version, candidate_fields, candidate_tags,
                    field_confidences, evidence, missing_fields, review_status, reviewed_at, created_at)
                VALUES (?, ?, 1, '{}', '[]', '{}', '{}', '[]', 'AUTO_BLOCKED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, snapshotId, jobId);
        unitStore.insert(new AiRegistrationUnitStore.RegistrationUnitInsert(snapshotId, 1, "고정 식당",
                "AUTO_CONFIRMED", null, "{}", "{}", result.restaurantId(), result.creatorId(), result.videoId(),
                result.visitId(), reusedResourcesJson, "WORKER", OffsetDateTime.now()));
        return unitStore.findBySnapshotId(snapshotId).get(0);
    }

    @Test
    @DisplayName("동일 식당의 다른 영상과 다른 유튜버를 새 방문으로 연결한다")
    void register_기존맛집새유튜버영상_맛집만재사용() {
        RegistrationResult first = registration.register(command("place", "channel-one", "video-one"));

        RegistrationResult second = registration.register(command("place", "channel-two", "video-two"));

        assertThat(second.restaurantId()).isEqualTo(first.restaurantId());
        assertThat(second.restaurantCreated()).isFalse();
        assertThat(second.creatorCreated()).isTrue();
        assertThat(second.videoCreated()).isTrue();
        assertThat(second.visitCreated()).isTrue();
        assertCounts(1, 2, 2, 2);
    }

    @Test
    @DisplayName("동일 식당·영상·유튜버 관계 반복은 네 자원을 모두 재사용한다")
    void register_동일관계반복_같은식별자와재사용결과() {
        VerifiedContentCommand command = command("place", "channel", "video");
        RegistrationResult first = registration.register(command);

        RegistrationResult repeated = registration.register(command);

        assertThat(repeated).isEqualTo(new RegistrationResult(first.restaurantId(), first.creatorId(),
                first.videoId(), first.visitId(), false, false, false, false));
        assertCounts(1, 1, 1, 1);
    }

    @Test
    @DisplayName("동시 요청은 unique 위반으로 트랜잭션을 깨뜨리지 않고 같은 방문에 수렴한다")
    void register_동시동일관계_단일방문에수렴() throws Exception {
        VerifiedContentCommand command = command("place", "channel", "video");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var task = (java.util.concurrent.Callable<RegistrationResult>) () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent start timed out");
                }
                return registration.register(command);
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            RegistrationResult left = first.get(20, TimeUnit.SECONDS);
            RegistrationResult right = second.get(20, TimeUnit.SECONDS);

            assertThat(right.restaurantId()).isEqualTo(left.restaurantId());
            assertThat(right.creatorId()).isEqualTo(left.creatorId());
            assertThat(right.videoId()).isEqualTo(left.videoId());
            assertThat(right.visitId()).isEqualTo(left.visitId());
            assertCounts(1, 1, 1, 1);
        }
    }

    @Test
    @DisplayName("영상의 기존 유튜버가 다르면 앞서 만든 신규 식당과 유튜버도 모두 롤백한다")
    void register_후속영상연결충돌_신규부분저장0건() {
        registration.register(command("existing-place", "existing-channel", "existing-video"));

        assertThatThrownBy(() -> registration.register(command("new-place", "new-channel", "existing-video")))
                .isInstanceOf(IllegalArgumentException.class);

        assertCounts(1, 1, 1, 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM restaurant WHERE kakao_place_id = 'new-place'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM creator WHERE external_channel_id = 'new-channel'",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("재사용 식당의 새 영상 등록을 보상해도 기존 식당과 방문은 보존한다")
    void deleteIfCreated_재사용맛집_기존관계보존() {
        RegistrationResult first = registration.register(command("place", "channel-one", "video-one"));
        RegistrationResult second = registration.register(command("place", "channel-two", "video-two"));

        compensate(second);

        assertCounts(1, 1, 1, 1);
        assertThat(jdbc.queryForObject("SELECT id FROM restaurant", UUID.class)).isEqualTo(first.restaurantId());
        assertThat(jdbc.queryForObject("SELECT id FROM visit", UUID.class)).isEqualTo(first.visitId());
    }

    @Test
    @DisplayName("처음 만든 식당이 후속 방문에 재사용됐으면 보상 삭제와 공개 상태 롤백에서도 보존한다")
    void rollback_다른방문이참조하는신규자원_보존() {
        RegistrationResult first = registration.register(command("place", "channel", "video-one"));
        RegistrationResult second = registration.register(command("place", "channel", "video-two"));

        rollbackStore.makePrivateIfCreated(UUID.randomUUID(), first.restaurantId(), true,
                first.creatorId(), true, first.videoId(), true, first.visitId(), true);

        assertThat(jdbc.queryForObject("SELECT publication_status FROM restaurant WHERE id = ?",
                String.class, first.restaurantId())).isEqualTo("PUBLIC");
        assertThat(jdbc.queryForObject("SELECT publication_status FROM creator WHERE id = ?",
                String.class, first.creatorId())).isEqualTo("PUBLIC");
        compensate(first);
        assertCounts(1, 1, 1, 1);
        assertThat(jdbc.queryForObject("SELECT id FROM visit", UUID.class)).isEqualTo(second.visitId());
    }

    @Test
    @DisplayName("기존 미완성 Video에 이번에 만든 Creator를 연결한 경우 Creator를 보존한다")
    void rollback_기존미완성영상에신규유튜버연결_공개참조보존() {
        // Given: PUBLIC Video가 기존 Creator와 Visit 없이 남아 있는 재사용 상태
        RegistrationResult initial = registration.register(command("place", "channel", "shared-video"));
        jdbc.update("DELETE FROM visit_tag WHERE visit_id = ?", initial.visitId());
        jdbc.update("DELETE FROM visit WHERE id = ?", initial.visitId());
        jdbc.update("UPDATE video SET creator_id = NULL WHERE id = ?", initial.videoId());
        jdbc.update("DELETE FROM creator WHERE id = ?", initial.creatorId());

        // When: 기존 Video에 신규 Creator를 연결하고, 해당 등록 단위를 롤백한다
        RegistrationResult registered = registration.register(command("place", "channel", "shared-video"));
        assertThat(registered.videoCreated()).isFalse();
        assertThat(registered.creatorCreated()).isTrue();
        var unit = registeredUnit(registered);
        assertThat(postCommit.rollback(unit, "재사용 영상 Creator 보존", reverted(registered), admin())).isTrue();

        // Then: 재사용 공개 Video가 참조하는 신규 Creator도 공개 상태를 유지한다
        assertThat(jdbc.queryForObject("SELECT creator_id FROM video WHERE id = ?", UUID.class,
                registered.videoId())).isEqualTo(registered.creatorId());
        assertThat(jdbc.queryForObject("SELECT publication_status FROM video WHERE id = ?", String.class,
                registered.videoId())).isEqualTo("PUBLIC");
        assertThat(jdbc.queryForObject("SELECT publication_status FROM creator WHERE id = ?", String.class,
                registered.creatorId())).isEqualTo("PUBLIC");
        assertThat(jdbc.queryForObject("SELECT publication_status FROM visit WHERE id = ?", String.class,
                registered.visitId())).isEqualTo("PRIVATE");
    }

    @Test
    @DisplayName("생성 여부 저장은 네 재사용 자원과 과거 두 자원 값을 모두 허용한다")
    void reusedResources_허용값확장_과거계약보존() {
        assertThat(jdbc.queryForObject("SELECT ai_registration_unit_reused_resources_are_valid(?::jsonb)",
                Boolean.class, "[\"restaurant\",\"creator\",\"video\",\"visit\"]")).isTrue();
        assertThat(jdbc.queryForObject("SELECT ai_registration_unit_reused_resources_are_valid(?::jsonb)",
                Boolean.class, "[\"creator\",\"video\"]")).isTrue();
        assertThat(jdbc.queryForObject("SELECT ai_registration_unit_reused_resources_are_valid(?::jsonb)",
                Boolean.class, "[\"unknown\"]")).isFalse();
    }

    private void compensate(RegistrationResult result) {
        rollbackStore.deleteIfCreated(result.restaurantId(), result.restaurantCreated(),
                result.creatorId(), result.creatorCreated(), result.videoId(), result.videoCreated(),
                result.visitId(), result.visitCreated());
    }

    private void assertCounts(int restaurants, int creators, int videos, int visits) {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM restaurant", Integer.class)).isEqualTo(restaurants);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM creator", Integer.class)).isEqualTo(creators);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM video", Integer.class)).isEqualTo(videos);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM visit", Integer.class)).isEqualTo(visits);
    }

    private VerifiedContentCommand command(String place, String channel, String video) {
        return new VerifiedContentCommand(
                new AutoRegisterVerifiedContentUseCase.RestaurantCandidate(REGION_ID, CATEGORY_ID, "고정 식당",
                        place, "https://place.map.kakao.com/" + place, "서울특별시 마포구 월드컵로 1", null,
                        "02-1234-5678", new BigDecimal("37.5"), new BigDecimal("126.9")),
                new AutoRegisterVerifiedContentUseCase.CreatorCandidate(channel, "고정 유튜버",
                        "https://www.youtube.com/channel/" + channel),
                new AutoRegisterVerifiedContentUseCase.VideoCandidate(video, channel, "고정 영상",
                        "https://www.youtube.com/watch?v=" + video, "https://example.com/fixed.jpg",
                        OffsetDateTime.parse("2026-10-14T00:00:00Z"), OffsetDateTime.parse("2026-10-14T00:00:00Z")), true);
    }
}
