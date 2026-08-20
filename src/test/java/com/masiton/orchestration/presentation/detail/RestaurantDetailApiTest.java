package com.masiton.orchestration.presentation.detail;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API-DETAIL-001 맛집 상세 조회를 실제 PostgreSQL과 MockMvc로 끝까지 검증한다.
 * Fixture는 각 테스트가 JdbcTemplate으로 직접 적재하고 다른 테스트가 만든 데이터에 의존하지 않는다.
 *
 * <p>{@link QueryCountingDataSourceConfiguration}이 {@code DataSource} Bean을 JDK 동적 Proxy로 감싸
 * {@code Connection.prepareStatement}/{@code prepareCall} 호출 횟수를 센다. 새 라이브러리 의존 없이
 * query-composition.md 6·11절이 요구하는 "상세 정상 경로 쿼리 수 2회"를 자동으로 검증하기 위함이다.
 */
@SpringBootTest
@com.masiton.test.TestProfile
@AutoConfigureMockMvc
@DisplayName("맛집 상세 조회 API")
class RestaurantDetailApiTest extends com.masiton.test.FullContextIntegrationTest {

    // seed-data-plan.md 2·3절 고정 기준 데이터. 초기 스키마 baseline이 적재하므로 참조만 하고 수정하지 않는다.
    private static final UUID SEED_FOOD_CATEGORY_ID =
            UUID.fromString("20000000-0000-4000-8000-000000000001");

    /** {@link QueryCountingDataSourceConfiguration}이 prepareStatement/prepareCall 호출마다 증가시킨다. */
    private static final AtomicInteger PREPARED_STATEMENT_COUNT = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("공개 맛집과 공개 방문 콘텐츠가 있으면 계약대로 조합한 200 응답을 반환하고 정확히 2개의 쿼리만 실행한다")
    void 상세조회_공개맛집과공개콘텐츠존재_200과전체응답을반환하고쿼리는정확히2회실행한다() throws Exception {
        // given
        UUID restaurantId = UUID.randomUUID();
        insertRestaurant(restaurantId, "테스트 맛집", "2층", "PUBLIC", "ACTIVE", null);
        UUID creatorId = UUID.randomUUID();
        String channelId = "UC-" + UUID.randomUUID();
        insertCreator(creatorId, channelId, "테스트 채널", "PUBLIC", "ACTIVE", "AVAILABLE", null);
        UUID videoId = UUID.randomUUID();
        insertVideo(videoId, creatorId, channelId, "테스트 영상", "PUBLIC", "ACTIVE", "AVAILABLE", null);
        insertVisit(UUID.randomUUID(), restaurantId, creatorId, videoId, "PUBLIC", "ACTIVE", null);

        // when
        PREPARED_STATEMENT_COUNT.set(0);
        mockMvc.perform(get("/api/restaurants/{restaurantId}", restaurantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(restaurantId.toString()))
                .andExpect(jsonPath("$.name").value("테스트 맛집"))
                .andExpect(jsonPath("$.category").value("한식"))
                .andExpect(jsonPath("$.address.roadAddress").value("서울특별시 종로구 테스트로 1"))
                .andExpect(jsonPath("$.address.detailAddress").value("2층"))
                .andExpect(jsonPath("$.phoneNumber").value("02-1234-5678"))
                .andExpect(jsonPath("$.kakaoPlaceUrl").value(not(emptyString())))
                .andExpect(jsonPath("$.contentStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.visitedBy", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.visitedBy[0].id").value(creatorId.toString()))
                .andExpect(jsonPath("$.visitedBy[0].channelName").value("테스트 채널"))
                .andExpect(jsonPath("$.visitedBy[0].channelUrl")
                        .value("https://www.youtube.com/channel/" + channelId))
                .andExpect(jsonPath("$.videos", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.videos[0].id").value(videoId.toString()))
                .andExpect(jsonPath("$.videos[0].title").value("테스트 영상"))
                .andExpect(jsonPath("$.videos[0].thumbnailUrl")
                        .value("https://i.ytimg.com/" + videoId + ".jpg"))
                .andExpect(jsonPath("$.videos[0].channelName").value("테스트 채널"))
                .andExpect(jsonPath("$.videos[0].sourceUrl")
                        .value("https://www.youtube.com/watch?v=" + videoId));

        // then: query-composition.md 6·11절 — 기본 정보 1회, 콘텐츠 1회, 총 2회.
        assertThat(PREPARED_STATEMENT_COUNT.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("상세 주소가 등록되지 않았으면 detailAddress는 null이다")
    void 상세조회_상세주소미등록_detailAddress가null이다() throws Exception {
        // given
        UUID restaurantId = UUID.randomUUID();
        insertRestaurant(restaurantId, "상세주소없는 맛집", null, "PUBLIC", "ACTIVE", null);

        // when & then
        mockMvc.perform(get("/api/restaurants/{restaurantId}", restaurantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address.detailAddress").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("방문 관계가 없으면 200과 AVAILABLE 상태의 빈 목록 두 개를 반환한다")
    void 상세조회_방문관계없음_200과AVAILABLE빈목록을반환한다() throws Exception {
        // given
        UUID restaurantId = UUID.randomUUID();
        insertRestaurant(restaurantId, "방문없는 맛집", null, "PUBLIC", "ACTIVE", null);

        // when
        PREPARED_STATEMENT_COUNT.set(0);
        mockMvc.perform(get("/api/restaurants/{restaurantId}", restaurantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.visitedBy", org.hamcrest.Matchers.hasSize(0)))
                .andExpect(jsonPath("$.videos", org.hamcrest.Matchers.hasSize(0)));

        // then
        assertThat(PREPARED_STATEMENT_COUNT.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("비공개 유튜버의 방문 관계는 제외하고 기본 정보는 유지한다")
    void 상세조회_비공개유튜버방문관계_콘텐츠에서제외하고기본정보는유지한다() throws Exception {
        // given
        UUID restaurantId = UUID.randomUUID();
        insertRestaurant(restaurantId, "비공개유튜버맛집", null, "PUBLIC", "ACTIVE", null);
        UUID creatorId = UUID.randomUUID();
        String channelId = "UC-" + UUID.randomUUID();
        insertCreator(creatorId, channelId, "비공개 채널", "PRIVATE", "ACTIVE", "AVAILABLE", null);
        UUID videoId = UUID.randomUUID();
        insertVideo(videoId, creatorId, channelId, "비공개채널의 영상", "PUBLIC", "ACTIVE", "AVAILABLE", null);
        insertVisit(UUID.randomUUID(), restaurantId, creatorId, videoId, "PUBLIC", "ACTIVE", null);

        // when & then
        mockMvc.perform(get("/api/restaurants/{restaurantId}", restaurantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("비공개유튜버맛집"))
                .andExpect(jsonPath("$.contentStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.visitedBy", org.hamcrest.Matchers.hasSize(0)))
                .andExpect(jsonPath("$.videos", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    @DisplayName("삭제된 영상을 근거로 한 방문 관계는 visitedBy와 videos에서 모두 제외한다")
    void 상세조회_삭제된영상방문관계_visitedBy와videos에서모두제외한다() throws Exception {
        // given: BR-VISIT-005 — 영상까지 공개·유효한 관계만 사용자 조회에 사용한다.
        UUID restaurantId = UUID.randomUUID();
        insertRestaurant(restaurantId, "삭제영상맛집", null, "PUBLIC", "ACTIVE", null);
        UUID creatorId = UUID.randomUUID();
        String channelId = "UC-" + UUID.randomUUID();
        insertCreator(creatorId, channelId, "정상 채널", "PUBLIC", "ACTIVE", "AVAILABLE", null);
        UUID videoId = UUID.randomUUID();
        insertVideo(videoId, creatorId, channelId, "삭제된 영상", "PRIVATE", "DELETED", "AVAILABLE", OffsetDateTime.now());
        insertVisit(UUID.randomUUID(), restaurantId, creatorId, videoId, "PUBLIC", "ACTIVE", null);

        // when & then
        mockMvc.perform(get("/api/restaurants/{restaurantId}", restaurantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("삭제영상맛집"))
                .andExpect(jsonPath("$.contentStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.visitedBy", org.hamcrest.Matchers.hasSize(0)))
                .andExpect(jsonPath("$.videos", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    @DisplayName("방문 관계가 다수여도 중복 유튜버를 한 번만 표시하고 정확히 2개의 쿼리만 실행한다")
    void 상세조회_방문관계다수_중복제거하고쿼리는정확히2회실행한다() throws Exception {
        // given
        UUID restaurantId = UUID.randomUUID();
        insertRestaurant(restaurantId, "다수방문 맛집", null, "PUBLIC", "ACTIVE", null);

        UUID creatorId1 = UUID.randomUUID();
        String channelId1 = "UC-" + UUID.randomUUID();
        insertCreator(creatorId1, channelId1, "가 채널", "PUBLIC", "ACTIVE", "AVAILABLE", null);
        UUID videoId1 = UUID.randomUUID();
        UUID videoId2 = UUID.randomUUID();
        insertVideo(videoId1, creatorId1, channelId1, "가 영상", "PUBLIC", "ACTIVE", "AVAILABLE", null);
        insertVideo(videoId2, creatorId1, channelId1, "나 영상", "PUBLIC", "ACTIVE", "AVAILABLE", null);
        insertVisit(UUID.randomUUID(), restaurantId, creatorId1, videoId1, "PUBLIC", "ACTIVE", null);
        insertVisit(UUID.randomUUID(), restaurantId, creatorId1, videoId2, "PUBLIC", "ACTIVE", null);

        UUID creatorId2 = UUID.randomUUID();
        String channelId2 = "UC-" + UUID.randomUUID();
        insertCreator(creatorId2, channelId2, "나 채널", "PUBLIC", "ACTIVE", "AVAILABLE", null);
        UUID videoId3 = UUID.randomUUID();
        insertVideo(videoId3, creatorId2, channelId2, "다 영상", "PUBLIC", "ACTIVE", "AVAILABLE", null);
        insertVisit(UUID.randomUUID(), restaurantId, creatorId2, videoId3, "PUBLIC", "ACTIVE", null);

        // when
        PREPARED_STATEMENT_COUNT.set(0);
        mockMvc.perform(get("/api/restaurants/{restaurantId}", restaurantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.visitedBy", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.visitedBy[0].id").value(creatorId1.toString()))
                .andExpect(jsonPath("$.visitedBy[1].id").value(creatorId2.toString()))
                .andExpect(jsonPath("$.videos", org.hamcrest.Matchers.hasSize(3)))
                .andExpect(jsonPath("$.videos[0].id").value(videoId1.toString()))
                .andExpect(jsonPath("$.videos[1].id").value(videoId2.toString()))
                .andExpect(jsonPath("$.videos[2].id").value(videoId3.toString()));

        // then
        assertThat(PREPARED_STATEMENT_COUNT.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("존재하지 않는 식별자는 404 RESTAURANT_NOT_FOUND를 반환한다")
    void 상세조회_존재하지않는식별자_404RESTAURANT_NOT_FOUND를반환한다() throws Exception {
        mockMvc.perform(get("/api/restaurants/{restaurantId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESTAURANT_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));
    }

    @Test
    @DisplayName("UUID 형식이 아닌 식별자는 400 INVALID_IDENTIFIER를 반환한다")
    void 상세조회_UUID형식아닌식별자_400INVALID_IDENTIFIER를반환한다() throws Exception {
        mockMvc.perform(get("/api/restaurants/{restaurantId}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDENTIFIER"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));
    }

    @Test
    @DisplayName("비공개 맛집은 존재 여부를 누설하지 않고 404 RESTAURANT_NOT_FOUND를 반환한다")
    void 상세조회_비공개맛집_404RESTAURANT_NOT_FOUND를반환한다() throws Exception {
        // given
        UUID restaurantId = UUID.randomUUID();
        insertRestaurant(restaurantId, "비공개 맛집", null, "PRIVATE", "ACTIVE", null);

        // when & then
        mockMvc.perform(get("/api/restaurants/{restaurantId}", restaurantId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESTAURANT_NOT_FOUND"));
    }

    @Test
    @DisplayName("삭제된 맛집은 존재 여부를 누설하지 않고 404 RESTAURANT_NOT_FOUND를 반환한다")
    void 상세조회_삭제된맛집_404RESTAURANT_NOT_FOUND를반환한다() throws Exception {
        // given
        UUID restaurantId = UUID.randomUUID();
        insertRestaurant(restaurantId, "삭제된 맛집", null, "PRIVATE", "DELETED", OffsetDateTime.now());

        // when & then
        mockMvc.perform(get("/api/restaurants/{restaurantId}", restaurantId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESTAURANT_NOT_FOUND"));
    }

    private void insertRestaurant(
            UUID id,
            String name,
            String detailAddress,
            String publicationStatus,
            String lifecycleStatus,
            OffsetDateTime deletedAt
    ) {
        UUID regionId = seedRegionId();
        jdbcTemplate.update(
                "INSERT INTO restaurant "
                        + "(id, region_id, food_category_id, name, kakao_place_id, kakao_place_url, "
                        + "road_address, detail_address, phone_number, publication_status, lifecycle_status, deleted_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                regionId,
                SEED_FOOD_CATEGORY_ID,
                name,
                "KAKAO-" + UUID.randomUUID(),
                "https://place.map.kakao.com/" + id,
                "서울특별시 종로구 테스트로 1",
                detailAddress,
                "02-1234-5678",
                publicationStatus,
                lifecycleStatus,
                deletedAt);
    }

    private void insertCreator(
            UUID id,
            String externalChannelId,
            String channelName,
            String publicationStatus,
            String lifecycleStatus,
            String externalAvailabilityStatus,
            OffsetDateTime deletedAt
    ) {
        jdbcTemplate.update(
                "INSERT INTO creator "
                        + "(id, external_channel_id, channel_name, channel_url, publication_status, "
                        + "lifecycle_status, external_availability_status, external_status_checked_at, deleted_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                externalChannelId,
                channelName,
                "https://www.youtube.com/channel/" + externalChannelId,
                publicationStatus,
                lifecycleStatus,
                externalAvailabilityStatus,
                OffsetDateTime.now(),
                deletedAt);
    }

    private void insertVideo(
            UUID id,
            UUID creatorId,
            String publisherExternalChannelId,
            String title,
            String publicationStatus,
            String lifecycleStatus,
            String externalAvailabilityStatus,
            OffsetDateTime deletedAt
    ) {
        jdbcTemplate.update(
                "INSERT INTO video "
                        + "(id, creator_id, external_video_id, publisher_external_channel_id, title, "
                        + "source_url, thumbnail_url, publication_status, lifecycle_status, "
                        + "external_availability_status, external_status_checked_at, deleted_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                creatorId,
                shortId("VID-"),
                publisherExternalChannelId,
                title,
                "https://www.youtube.com/watch?v=" + id,
                "https://i.ytimg.com/" + id + ".jpg",
                publicationStatus,
                lifecycleStatus,
                externalAvailabilityStatus,
                OffsetDateTime.now(),
                deletedAt);
    }

    private void insertVisit(
            UUID id,
            UUID restaurantId,
            UUID creatorId,
            UUID videoId,
            String publicationStatus,
            String lifecycleStatus,
            OffsetDateTime deletedAt
    ) {
        jdbcTemplate.update(
                "INSERT INTO visit "
                        + "(id, restaurant_id, creator_id, video_id, publication_status, lifecycle_status, deleted_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,
                restaurantId,
                creatorId,
                videoId,
                publicationStatus,
                lifecycleStatus,
                deletedAt);
    }

    private UUID seedRegionId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM region ORDER BY sort_order LIMIT 1", UUID.class);
    }

    /** varchar(32) 컬럼(external_video_id)에 맞도록 UUID를 잘라 짧은 식별자를 만든다. */
    private String shortId(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 20);
    }

    /**
     * 운영 {@code DataSource} Bean을 JDK 동적 Proxy로 감싸 {@code Connection.prepareStatement}/
     * {@code prepareCall} 호출 횟수를 센다. Mockito·AOP 프레임워크나 새 라이브러리 의존 없이
     * JDK 표준 {@link Proxy}만으로 query-composition.md 6절의 "N+1 없음, 총 2 query"를 검증하기 위함이다.
     */
    @TestConfiguration
    static class QueryCountingDataSourceConfiguration {

        @Bean
        static BeanPostProcessor queryCountingDataSourceBeanPostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof DataSource dataSource && !Proxy.isProxyClass(bean.getClass())) {
                        return Proxy.newProxyInstance(
                                DataSource.class.getClassLoader(),
                                new Class<?>[] {DataSource.class},
                                new CountingDataSourceInvocationHandler(dataSource));
                    }
                    return bean;
                }
            };
        }
    }

    private record CountingDataSourceInvocationHandler(DataSource delegate) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result = invokeDelegate(delegate, method, args);
            if ("getConnection".equals(method.getName()) && result instanceof Connection connection) {
                return Proxy.newProxyInstance(
                        Connection.class.getClassLoader(),
                        new Class<?>[] {Connection.class},
                        new CountingConnectionInvocationHandler(connection));
            }
            return result;
        }
    }

    private record CountingConnectionInvocationHandler(Connection delegate) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            if ("prepareStatement".equals(methodName) || "prepareCall".equals(methodName)) {
                PREPARED_STATEMENT_COUNT.incrementAndGet();
            }
            return invokeDelegate(delegate, method, args);
        }
    }

    private static Object invokeDelegate(Object delegate, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}
