package com.masiton.orchestration;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.masiton.common.web.BusinessException;
import com.masiton.orchestration.application.port.in.RegisterVisitRelationshipUseCase;
import com.masiton.security.application.AdminPrincipal;
import com.masiton.security.application.AdminRole;
import com.masiton.video.application.port.in.ResolveVideoCreatorUseCase;
import com.masiton.visit.application.port.in.CreatorRestaurantCandidates;
import com.masiton.visit.application.port.in.FindDistinctValidRestaurantIdsByCreatorQuery;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@com.masiton.test.TestProfile
@DisplayName("방문 관계 등록 통합")
class VisitRelationshipRegistrationIntegrationTest extends com.masiton.test.FullContextIntegrationTest {

    private static final UUID SEED_REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID SEED_FOOD_CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");

    @Autowired
    private RegisterVisitRelationshipUseCase registerVisitRelationshipUseCase;

    @Autowired
    private FindDistinctValidRestaurantIdsByCreatorQuery creatorRestaurantQuery;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ResolveVideoCreatorUseCase videoCreatorResolver;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("동시 등록은 Visit 한 건만 커밋하고 미연결 Video를 Creator에 연결하며 공개 조회에 반영한다")
    void register_동시요청_한건만커밋하고공개조회에반영한다() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        String channelId = "UC-" + UUID.randomUUID();
        insertRestaurant(restaurantId);
        insertCreator(creatorId, channelId);
        insertVideo(videoId, null, channelId);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = List.of(
                    executor.submit(() -> registerConcurrently(restaurantId, creatorId, videoId, ready, start)),
                    executor.submit(() -> registerConcurrently(restaurantId, creatorId, videoId, ready, start)));
            ready.await();
            start.countDown();

            List<Boolean> created = List.of(results.get(0).get(), results.get(1).get());
            assertThat(created).containsExactlyInAnyOrder(true, false);
        }

        Integer visitCount = jdbcTemplate.queryForObject(
                "select count(*) from visit where restaurant_id = ? and creator_id = ? and video_id = ?",
                Integer.class, restaurantId, creatorId, videoId);
        UUID resolvedCreatorId = jdbcTemplate.queryForObject(
                "select creator_id from video where id = ?", UUID.class, videoId);
        CreatorRestaurantCandidates candidates = creatorRestaurantQuery
                .findDistinctValidRestaurantIdsByCreator(creatorId);

        assertThat(visitCount).isEqualTo(1);
        assertThat(resolvedCreatorId).isEqualTo(creatorId);
        assertThat(candidates.creatorPublic()).isTrue();
        assertThat(candidates.restaurantIds()).contains(restaurantId);
    }

    @Test
    @DisplayName("Creator 연결 뒤 Visit 생성이 실패하면 Video 연결과 Visit 저장이 함께 롤백된다")
    void register_VisitCreationFailure_creatorAttachmentAndVisitPersistenceRollbackTogether() {
        // given
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        String channelId = "UC-" + UUID.randomUUID();
        insertRestaurant(restaurantId);
        insertCreator(creatorId, channelId);
        insertVideo(videoId, null, channelId);

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        // when
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            videoCreatorResolver.resolveCreator(videoId, creatorId);
            throw new IllegalStateException("Visit persistence failed after creator attachment.");
        })).isInstanceOf(IllegalStateException.class);

        // then
        Integer visitCount = jdbcTemplate.queryForObject(
                "select count(*) from visit where restaurant_id = ? and creator_id = ? and video_id = ?",
                Integer.class, restaurantId, creatorId, videoId);
        UUID resolvedCreatorId = jdbcTemplate.queryForObject(
                "select creator_id from video where id = ?", UUID.class, videoId);
        assertThat(visitCount).isZero();
        assertThat(resolvedCreatorId).isNull();
    }

    private boolean registerConcurrently(
            UUID restaurantId,
            UUID creatorId,
            UUID videoId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await();
        try {
            registerVisitRelationshipUseCase.register(
                    new RegisterVisitRelationshipUseCase.RegisterVisitRelationshipCommand(
                            restaurantId, creatorId, videoId, true),
                    adminPrincipal());
            return true;
        } catch (BusinessException exception) {
            assertThat(exception.code()).isEqualTo("DUPLICATE_VISIT_RELATIONSHIP");
            return false;
        }
    }

    private void insertRestaurant(UUID id) {
        String externalId = "KAKAO-" + UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into restaurant (id, region_id, food_category_id, name, kakao_place_id, kakao_place_url,
                                        road_address, phone_number)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, SEED_REGION_ID, SEED_FOOD_CATEGORY_ID, "통합 테스트 맛집", externalId,
                "https://example.com/place/" + externalId, "서울특별시 종로구 테스트로 1", "02-1234-5678");
    }

    private void insertCreator(UUID id, String channelId) {
        jdbcTemplate.update(
                """
                insert into creator (id, external_channel_id, channel_name, channel_url, external_status_checked_at)
                values (?, ?, ?, ?, ?)
                """,
                id, channelId, "통합 테스트 채널", "https://example.com/channel/" + channelId, OffsetDateTime.now());
    }

    private void insertVideo(UUID id, UUID creatorId, String channelId) {
        String externalVideoId = "VID-" + UUID.randomUUID().toString().substring(0, 20);
        jdbcTemplate.update(
                """
                insert into video (id, creator_id, external_video_id, publisher_external_channel_id, title,
                                   source_url, thumbnail_url, external_status_checked_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, creatorId, externalVideoId, channelId, "통합 테스트 영상",
                "https://example.com/video/" + externalVideoId,
                "https://example.com/thumbnail/" + externalVideoId,
                OffsetDateTime.now());
    }

    private AdminPrincipal adminPrincipal() {
        return new AdminPrincipal("admin-id", java.util.Set.of(AdminRole.ADMIN));
    }
}
