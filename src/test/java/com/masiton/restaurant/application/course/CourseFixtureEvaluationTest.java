package com.masiton.restaurant.application.course;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Named;

import com.masiton.common.web.BusinessException;
import com.masiton.restaurant.application.port.in.RestaurantCourseCommand;
import com.masiton.restaurant.application.port.in.RestaurantCourseResult;
import com.masiton.restaurant.application.port.in.RestaurantCourseStop;
import com.masiton.restaurant.application.port.out.CourseRouteFailureCategory;
import com.masiton.restaurant.application.port.out.CourseRouteLeg;
import com.masiton.restaurant.application.port.out.CourseRouteProviderException;
import com.masiton.restaurant.application.port.out.CourseRouteProviderPort;
import com.masiton.restaurant.application.port.out.CourseRouteResult;
import com.masiton.restaurant.application.port.out.RestaurantRepositoryPort;
import com.masiton.restaurant.domain.model.LifecycleStatus;
import com.masiton.restaurant.domain.model.PublicationStatus;
import com.masiton.restaurant.domain.model.Restaurant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 근거: docs/08-planning/third-expansion-evaluation-strategy.md 3.3·4.4절,
 * docs/08-planning/third-expansion-test-matrix.md, EVAL-COURSE-001~005.
 *
 * {@code src/test/resources/eval/course-fixture-v1.0.0/cases.json}에 고정된 결정론적 Fixture 60건을
 * {@link RestaurantCourseRecommendationService}에 직접 실행해 프로그램 평가로 판정한다. LLM 심판·종합 점수를
 * 만들지 않고 EVAL ID별 위반 건수만 집계한다(전략 6.1절). Kakao Mobility·YouTube 실제 API를 호출하지 않으며
 * {@link CourseRouteProviderPort}는 Mockito mock으로 대체한다.
 *
 * <p>이 클래스가 {@code com.masiton.restaurant.application.course}에 있는 이유는 판정 대상 SUT인
 * {@link RestaurantCourseRecommendationService}가 이 패키지에 있고, 테스트 패키지 구조가 운영 패키지 구조를
 * 반영해야 하기 때문이다(docs/06-architecture/package-structure.md 8절). {@code course}/{@code route}를
 * 별도 최상위 도메인으로 신설하지 않는다(docs/02-analysis/third-expansion-domain-boundaries.md 9절).
 *
 * <p><b>Release holdout 게이트(전략 4.3절):</b> {@code RELEASE_HOLDOUT} 분할은 변경 튜닝에 사용하지 않고
 * 출시 판정 시에만 실행한다. 기본 {@code ./gradlew test} 실행은 {@code DEVELOPMENT}·{@code CALIBRATION}
 * 48건만 판정하며 {@code RELEASE_HOLDOUT} 12건은 시스템 프로퍼티
 * {@value #RELEASE_HOLDOUT_PROPERTY}가 {@code true}일 때만 포함된다. 출시 판정 실행 명령은 다음과 같다.
 * <pre>{@code
 * ./gradlew.bat test --tests "com.masiton.restaurant.application.course.CourseFixtureEvaluationTest" -Dmasiton.eval.releaseHoldout=true --no-daemon
 * }</pre>
 */
@DisplayName("맛집 코스 추천 결정론적 Fixture 평가(EVAL-COURSE-001~005)")
class CourseFixtureEvaluationTest {

    private static final String FIXTURE_PATH = "/eval/course-fixture-v1.0.0/cases.json";
    private static final String RELEASE_HOLDOUT_PROPERTY = "masiton.eval.releaseHoldout";
    private static final String RELEASE_HOLDOUT_SPLIT = "RELEASE_HOLDOUT";

    // -------------------------------------------------------------------
    // Fixture 사례 로딩
    // -------------------------------------------------------------------

    private static List<FixtureCase> loadCases() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = CourseFixtureEvaluationTest.class.getResourceAsStream(FIXTURE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Fixture 파일을 찾을 수 없습니다: " + FIXTURE_PATH);
            }
            JsonNode root = mapper.readTree(in);
            List<FixtureCase> cases = new ArrayList<>();
            for (JsonNode caseNode : root.get("cases")) {
                cases.add(parseCase(caseNode));
            }
            return List.copyOf(cases);
        } catch (IOException e) {
            throw new IllegalStateException("Fixture 파일을 읽을 수 없습니다: " + FIXTURE_PATH, e);
        }
    }

    private static FixtureCase parseCase(JsonNode node) {
        List<String> evalIds = textList(node.get("evalIds"));
        List<String> requestedIds = textList(node.get("requestedRestaurantIds"));
        List<FixtureRestaurant> restaurants = new ArrayList<>();
        for (JsonNode r : node.get("restaurants")) {
            restaurants.add(new FixtureRestaurant(
                    r.get("id").asText(),
                    r.get("publicationStatus").asText(),
                    r.get("lifecycleStatus").asText(),
                    nullableDouble(r.get("latitude")),
                    nullableDouble(r.get("longitude"))));
        }
        List<CourseRouteLeg> providerLegs = new ArrayList<>();
        for (JsonNode legNode : node.get("providerLegs")) {
            providerLegs.add(new CourseRouteLeg(legNode.get("distanceMeters").asInt(), legNode.get("durationSeconds").asInt()));
        }

        return new FixtureCase(
                node.get("caseId").asText(),
                node.get("split").asText(),
                evalIds,
                node.get("boundary").asText(),
                node.get("clockInstant").asText(),
                node.get("requeryCount").asInt(),
                node.get("expectedProviderCallCountPerRequest").asInt(),
                node.get("providerMode").asText(),
                providerLegs,
                nullableText(node.get("providerFailureCategory")),
                node.get("expectedOutcome").asText(),
                textListOrNull(node.get("expectedStopOrder")),
                textListOrNull(node.get("expectedRoles")),
                nullableInt(node.get("expectedTotalDistanceMeters")),
                nullableInt(node.get("expectedTotalDurationSeconds")),
                nullableInt(node.get("expectedExpiresAtPlusMinutes")),
                nullableText(node.get("expectedErrorCode")),
                nullableText(node.get("expectedHttpStatus")),
                nullableText(node.get("expectedFailureCategoryPublic")),
                nullableInt(node.get("expectedInvalidInputOrder")),
                requestedIds,
                restaurants);
    }

    private static List<String> textList(JsonNode arrayNode) {
        List<String> values = new ArrayList<>();
        for (JsonNode element : arrayNode) {
            values.add(element.asText());
        }
        return values;
    }

    private static List<String> textListOrNull(JsonNode arrayNode) {
        if (arrayNode == null || arrayNode.isNull()) {
            return null;
        }
        return textList(arrayNode);
    }

    private static String nullableText(JsonNode valueNode) {
        return (valueNode == null || valueNode.isNull()) ? null : valueNode.asText();
    }

    private static Integer nullableInt(JsonNode valueNode) {
        return (valueNode == null || valueNode.isNull()) ? null : valueNode.asInt();
    }

    private static Double nullableDouble(JsonNode valueNode) {
        return (valueNode == null || valueNode.isNull()) ? null : valueNode.asDouble();
    }

    private static boolean releaseHoldoutEnabled() {
        return Boolean.getBoolean(RELEASE_HOLDOUT_PROPERTY);
    }

    static Stream<Arguments> allCases() {
        return loadCases().stream()
                .filter(tc -> releaseHoldoutEnabled() || !RELEASE_HOLDOUT_SPLIT.equals(tc.split()))
                .map(tc -> Arguments.of(Named.of(tc.caseId() + " - " + tc.boundary(), tc)));
    }

    private static Stream<FixtureCase> casesForEval(String evalId) {
        return loadCases().stream()
                .filter(tc -> tc.evalIds().contains(evalId))
                .filter(tc -> releaseHoldoutEnabled() || !RELEASE_HOLDOUT_SPLIT.equals(tc.split()));
    }

    // -------------------------------------------------------------------
    // 데이터셋 구조 검증 (분할 게이트와 무관하게 항상 60건 구조를 검증한다)
    // -------------------------------------------------------------------

    @Test
    @DisplayName("Fixture 데이터셋은 60건이고 Development 36건, Calibration 12건, Release holdout 12건으로 분할된다")
    void 데이터셋은_60건이고_36대12대12로_분할된다() {
        List<FixtureCase> cases = loadCases();

        assertThat(cases).hasSize(60);

        Map<String, Long> counts = cases.stream()
                .collect(Collectors.groupingBy(FixtureCase::split, Collectors.counting()));
        assertThat(counts.getOrDefault("DEVELOPMENT", 0L)).isEqualTo(36L);
        assertThat(counts.getOrDefault("CALIBRATION", 0L)).isEqualTo(12L);
        assertThat(counts.getOrDefault(RELEASE_HOLDOUT_SPLIT, 0L)).isEqualTo(12L);
    }

    @Test
    @DisplayName("모든 Fixture 사례의 caseId는 서로 중복되지 않는다")
    void 모든사례의_caseId는_서로중복되지않는다() {
        List<FixtureCase> cases = loadCases();
        long distinctCount = cases.stream().map(FixtureCase::caseId).distinct().count();
        assertThat(distinctCount).isEqualTo(cases.size());
    }

    @Test
    @DisplayName("모든 EVAL-COURSE ID는 최소 한 건 이상의 Fixture 사례에서 판정된다")
    void 모든EVAL_ID는_최소한건이상판정된다() {
        List<FixtureCase> cases = loadCases();
        List<String> evalIds = List.of(
                "EVAL-COURSE-001", "EVAL-COURSE-002", "EVAL-COURSE-003", "EVAL-COURSE-004", "EVAL-COURSE-005");
        for (String evalId : evalIds) {
            long matching = cases.stream().filter(tc -> tc.evalIds().contains(evalId)).count();
            assertThat(matching).as("evalId=%s", evalId).isGreaterThan(0);
        }
    }

    // -------------------------------------------------------------------
    // 사례별 실행 (프로그램 평가). 기본 실행은 RELEASE_HOLDOUT을 제외한다.
    // -------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("allCases")
    @DisplayName("Fixture 사례는 기대한 성공·오류·외부 호출 횟수 결과와 일치한다")
    void 사례가_기대한결과와_일치한다(FixtureCase testCase) {
        List<String> violations = evaluate(testCase);
        assertThat(violations).as("case=%s (%s)", testCase.caseId(), testCase.boundary()).isEmpty();
    }

    // -------------------------------------------------------------------
    // EVAL ID별 위반 건수 집계 (출시 차단 게이트)
    // -------------------------------------------------------------------

    @Test
    @DisplayName("EVAL-COURSE-001(입력 경계: 2~5개·중복·첫 출발점·공개·좌표) 위반이 0건이다")
    void EVAL_COURSE_001_위반이_0건이다() {
        assertNoViolations("EVAL-COURSE-001");
    }

    @Test
    @DisplayName("EVAL-COURSE-002(경로 계약: 첫 출발점 유지·구간·전체 거리/시간·30km 상한) 위반이 0건이다")
    void EVAL_COURSE_002_위반이_0건이다() {
        assertNoViolations("EVAL-COURSE-002");
    }

    @Test
    @DisplayName("EVAL-COURSE-003(실패 안전성: timeout·429·부분 구간 실패 때 추정값 미노출) 위반이 0건이다")
    void EVAL_COURSE_003_위반이_0건이다() {
        assertNoViolations("EVAL-COURSE-003");
    }

    @Test
    @DisplayName("EVAL-COURSE-004(만료·재조회: 생성·만료 시각과 재조회 요구) 위반이 0건이다")
    void EVAL_COURSE_004_위반이_0건이다() {
        assertNoViolations("EVAL-COURSE-004");
    }

    // EVAL-COURSE-005의 quota hard stop(월 한도 도달 시 즉시 차단) 증거는 이 클래스가 아니라
    // KakaoMobilityCourseRouteAdapterWireMockIntegrationTest·RedisCourseRouteQuotaIntegrationTest에 있다.
    // 이 클래스는 CourseRouteProviderPort를 mock으로 대체하므로 quota 컴포넌트가 객체 그래프에 없고,
    // 여기서는 코스 1건당 Port 호출 수 상한(정확히 1회, 재조회 포함)만 판정한다.
    @Test
    @DisplayName("EVAL-COURSE-005(호출 비용: 코스당 Port 호출 수 상한, 재조회 포함) 위반이 0건이다")
    void EVAL_COURSE_005_위반이_0건이다() {
        assertNoViolations("EVAL-COURSE-005");
    }

    private void assertNoViolations(String evalId) {
        List<String> violations = new ArrayList<>();
        List<FixtureCase> matching = casesForEval(evalId).toList();
        assertThat(matching).as("evalId=%s에 해당하는 Fixture 사례가 없습니다", evalId).isNotEmpty();
        for (FixtureCase testCase : matching) {
            violations.addAll(evaluate(testCase));
        }
        assertThat(violations).as("evalId=%s", evalId).isEmpty();
    }

    // -------------------------------------------------------------------
    // 사례 실행과 판정 로직
    // -------------------------------------------------------------------

    private List<String> evaluate(FixtureCase testCase) {
        List<String> violations = new ArrayList<>();

        RestaurantRepositoryPort restaurantRepositoryPort = mock(RestaurantRepositoryPort.class);
        CourseRouteProviderPort courseRouteProviderPort = mock(CourseRouteProviderPort.class);
        Clock clock = Clock.fixed(Instant.parse(testCase.clockInstant()), ZoneOffset.UTC);
        RestaurantCourseRecommendationService service =
                new RestaurantCourseRecommendationService(restaurantRepositoryPort, courseRouteProviderPort, clock);

        List<Restaurant> found = testCase.restaurants().stream()
                .map(fr -> toRestaurant(fr, clock))
                .toList();
        when(restaurantRepositoryPort.findAllByIds(anyCollection())).thenReturn(found);

        switch (testCase.providerMode()) {
            case "SUCCESS", "PARTIAL_LEGS" ->
                    when(courseRouteProviderPort.calculate(any())).thenReturn(new CourseRouteResult(testCase.providerLegs()));
            case "THROW" -> when(courseRouteProviderPort.calculate(any()))
                    .thenThrow(new CourseRouteProviderException(
                            CourseRouteFailureCategory.valueOf(testCase.providerFailureCategory())));
            case "NOT_INVOKED" -> {
                // 호출되지 않아야 하므로 별도 stub이 필요 없다.
            }
            default -> violations.add("알 수 없는 providerMode: " + testCase.providerMode());
        }

        RestaurantCourseCommand command = new RestaurantCourseCommand(
                testCase.requestedRestaurantIds().stream().map(UUID::fromString).toList());

        boolean expectSuccess = "SUCCESS".equals(testCase.expectedOutcome());

        for (int attempt = 1; attempt <= testCase.requeryCount(); attempt++) {
            if (expectSuccess) {
                violations.addAll(evaluateSuccessAttempt(testCase, service, command, attempt));
            } else {
                violations.addAll(evaluateErrorAttempt(testCase, service, command, attempt));
            }
        }

        int expectedTotalCalls = testCase.expectedProviderCallCountPerRequest() * testCase.requeryCount();
        try {
            verify(courseRouteProviderPort, times(expectedTotalCalls)).calculate(any());
        } catch (AssertionError e) {
            violations.add("외부 호출 횟수 불일치: " + e.getMessage());
        }

        return violations;
    }

    private List<String> evaluateSuccessAttempt(
            FixtureCase testCase,
            RestaurantCourseRecommendationService service,
            RestaurantCourseCommand command,
            int attempt) {
        List<String> violations = new ArrayList<>();
        RestaurantCourseResult result;
        try {
            result = service.recommend(command);
        } catch (RuntimeException e) {
            violations.add("attempt=" + attempt + ": 성공을 기대했지만 예외가 발생했습니다: " + e);
            return violations;
        }

        List<String> actualStopOrder = result.restaurants().stream()
                .map(RestaurantCourseStop::restaurantId)
                .map(UUID::toString)
                .toList();
        if (testCase.expectedStopOrder() != null && !testCase.expectedStopOrder().equals(actualStopOrder)) {
            violations.add("attempt=" + attempt + ": stop 순서 불일치. expected=" + testCase.expectedStopOrder()
                    + ", actual=" + actualStopOrder);
        }

        List<String> actualRoles = result.restaurants().stream()
                .map(stop -> stop.role().name())
                .toList();
        if (testCase.expectedRoles() != null && !testCase.expectedRoles().equals(actualRoles)) {
            violations.add("attempt=" + attempt + ": role 불일치. expected=" + testCase.expectedRoles()
                    + ", actual=" + actualRoles);
        }

        List<Integer> expectedSequence = java.util.stream.IntStream
                .rangeClosed(1, result.restaurants().size()).boxed().toList();
        List<Integer> actualSequence = result.restaurants().stream().map(RestaurantCourseStop::sequence).toList();
        if (!expectedSequence.equals(actualSequence)) {
            violations.add("attempt=" + attempt + ": sequence가 1부터 연속이지 않습니다. actual=" + actualSequence);
        }

        if (testCase.expectedTotalDistanceMeters() != null
                && result.totalDistanceMeters() != testCase.expectedTotalDistanceMeters()) {
            violations.add("attempt=" + attempt + ": totalDistanceMeters 불일치. expected="
                    + testCase.expectedTotalDistanceMeters() + ", actual=" + result.totalDistanceMeters());
        }

        if (testCase.expectedTotalDurationSeconds() != null
                && result.totalDurationSeconds() != testCase.expectedTotalDurationSeconds()) {
            violations.add("attempt=" + attempt + ": totalDurationSeconds 불일치. expected="
                    + testCase.expectedTotalDurationSeconds() + ", actual=" + result.totalDurationSeconds());
        }

        if (result.totalDistanceMeters() > 30_000) {
            violations.add("attempt=" + attempt + ": 성공 결과의 totalDistanceMeters가 30km 상한을 초과했습니다. actual="
                    + result.totalDistanceMeters());
        }

        OffsetDateTime expectedGeneratedAt = OffsetDateTime.now(Clock.fixed(
                Instant.parse(testCase.clockInstant()), ZoneOffset.UTC));
        if (!result.generatedAt().isEqual(expectedGeneratedAt)) {
            violations.add("attempt=" + attempt + ": generatedAt이 고정 Clock과 일치하지 않습니다. expected="
                    + expectedGeneratedAt + ", actual=" + result.generatedAt());
        }

        int ttlMinutes = testCase.expectedExpiresAtPlusMinutes() == null ? 5 : testCase.expectedExpiresAtPlusMinutes();
        OffsetDateTime expectedExpiresAt = result.generatedAt().plusMinutes(ttlMinutes);
        if (!result.expiresAt().isEqual(expectedExpiresAt)) {
            violations.add("attempt=" + attempt + ": expiresAt이 generatedAt+5분과 일치하지 않습니다. expected="
                    + expectedExpiresAt + ", actual=" + result.expiresAt());
        }

        return violations;
    }

    private List<String> evaluateErrorAttempt(
            FixtureCase testCase,
            RestaurantCourseRecommendationService service,
            RestaurantCourseCommand command,
            int attempt) {
        List<String> violations = new ArrayList<>();
        try {
            RestaurantCourseResult unexpected = service.recommend(command);
            violations.add("attempt=" + attempt + ": 오류(" + testCase.expectedErrorCode()
                    + ")를 기대했지만 정상 결과가 반환됐습니다: " + unexpected);
            return violations;
        } catch (RestaurantCourseException e) {
            if (!testCase.expectedErrorCode().equals(e.code())) {
                violations.add("attempt=" + attempt + ": 오류 코드 불일치. expected="
                        + testCase.expectedErrorCode() + ", actual=" + e.code());
            }
            if (!testCase.expectedHttpStatus().equals(e.status().name())) {
                violations.add("attempt=" + attempt + ": HTTP 상태 불일치. expected="
                        + testCase.expectedHttpStatus() + ", actual=" + e.status().name());
            }
            if (testCase.expectedFailureCategoryPublic() != null) {
                Object details = e.details();
                if (!(details instanceof RestaurantCourseFailureDetails failureDetails)) {
                    violations.add("attempt=" + attempt
                            + ": failureCategory가 공개돼야 하지만 details가 없습니다(추정값 노출 여부를 판단할 수 없습니다).");
                } else {
                    if (!testCase.expectedFailureCategoryPublic().equals(failureDetails.failureCategory())) {
                        violations.add("attempt=" + attempt + ": 공개 failureCategory 불일치. expected="
                                + testCase.expectedFailureCategoryPublic()
                                + ", actual=" + failureDetails.failureCategory());
                    }
                    for (RestaurantCourseFailureDetails.SelectedRestaurant selected
                            : failureDetails.selectedRestaurants()) {
                        if (selected.restaurantId() == null || selected.name() == null) {
                            violations.add("attempt=" + attempt + ": 실패 응답의 선택 맛집 최소 표시 정보가 누락됐습니다.");
                        }
                    }
                    // EVAL-COURSE-003: 실패 응답은 계산된 거리·시간 추정값을 노출하지 않는다. details를 실제로
                    // 직렬화해 distance·duration 계열 키가 전혀 없는지 판정한다(타입 구조만으로는 필드 추가를
                    // 놓칠 수 있으므로 직렬화 결과를 기준으로 판정한다).
                    violations.addAll(assertNoDistanceOrDurationExposed(attempt, failureDetails));
                }
            } else if (testCase.expectedInvalidInputOrder() != null) {
                Object details = e.details();
                if (!(details instanceof RestaurantCourseSelectionDetails selectionDetails)) {
                    violations.add("attempt=" + attempt
                            + ": 문제 맛집 식별 정보가 있어야 하지만 details가 없습니다.");
                } else if (selectionDetails.selectedRestaurants().size() != 1) {
                    violations.add("attempt=" + attempt + ": 문제 맛집 식별 정보는 정확히 한 건이어야 합니다. actual="
                            + selectionDetails.selectedRestaurants());
                } else {
                    RestaurantCourseFailureDetails.SelectedRestaurant selected =
                            selectionDetails.selectedRestaurants().getFirst();
                    int expectedInputOrder = testCase.expectedInvalidInputOrder();
                    if (expectedInputOrder < 1 || expectedInputOrder > testCase.requestedRestaurantIds().size()) {
                        violations.add("attempt=" + attempt + ": Fixture의 expectedInvalidInputOrder 범위가 잘못됐습니다: "
                                + expectedInputOrder);
                    } else {
                        String expectedRestaurantId = testCase.requestedRestaurantIds().get(expectedInputOrder - 1);
                        if (!expectedRestaurantId.equals(selected.restaurantId())) {
                            violations.add("attempt=" + attempt + ": 문제 맛집 ID 불일치. expected="
                                    + expectedRestaurantId + ", actual=" + selected.restaurantId());
                        }
                    }
                    if (selected.inputOrder() != expectedInputOrder) {
                        violations.add("attempt=" + attempt + ": 문제 맛집 입력 순서 불일치. expected="
                                + expectedInputOrder + ", actual=" + selected.inputOrder());
                    }
                    if (selected.name() == null || selected.name().isBlank()) {
                        violations.add("attempt=" + attempt + ": 문제 맛집 이름이 누락됐습니다.");
                    }
                }
            } else if (e.details() != null) {
                violations.add("attempt=" + attempt + ": details가 없어야 하는 오류인데 details가 포함돼 있습니다: " + e.details());
            }
            return violations;
        } catch (BusinessException e) {
            violations.add("attempt=" + attempt + ": RestaurantCourseException이 아닌 BusinessException이 발생했습니다: " + e);
            return violations;
        }
    }

    private static final ObjectMapper FAILURE_DETAILS_MAPPER = new ObjectMapper();
    private static final List<String> DISTANCE_OR_DURATION_KEYS = List.of(
            "distanceMeters", "durationSeconds", "totalDistanceMeters", "totalDurationSeconds",
            "distance", "duration");

    private List<String> assertNoDistanceOrDurationExposed(
            int attempt, RestaurantCourseFailureDetails failureDetails) {
        List<String> violations = new ArrayList<>();
        String serialized = FAILURE_DETAILS_MAPPER.writeValueAsString(failureDetails);
        for (String key : DISTANCE_OR_DURATION_KEYS) {
            if (serialized.contains("\"" + key + "\"")) {
                violations.add("attempt=" + attempt + ": 실패 응답 details에 거리·시간 추정값 키(" + key + ")가 노출됐습니다: "
                        + serialized);
            }
        }
        return violations;
    }

    private Restaurant toRestaurant(FixtureRestaurant fr, Clock clock) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return new Restaurant(
                UUID.fromString(fr.id()),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "평가 Fixture 맛집 " + fr.id(),
                "kakao-place-" + fr.id(),
                "https://place.map.kakao.com/" + fr.id(),
                "서울특별시 종로구 평가로 1",
                null,
                null,
                fr.latitude() == null ? null : BigDecimal.valueOf(fr.latitude()),
                fr.longitude() == null ? null : BigDecimal.valueOf(fr.longitude()),
                PublicationStatus.valueOf(fr.publicationStatus()),
                LifecycleStatus.valueOf(fr.lifecycleStatus()),
                now,
                now,
                null);
    }

    // -------------------------------------------------------------------
    // Fixture 사례 모델
    // -------------------------------------------------------------------

    private record FixtureRestaurant(
            String id, String publicationStatus, String lifecycleStatus, Double latitude, Double longitude) {
    }

    private record FixtureCase(
            String caseId,
            String split,
            List<String> evalIds,
            String boundary,
            String clockInstant,
            int requeryCount,
            int expectedProviderCallCountPerRequest,
            String providerMode,
            List<CourseRouteLeg> providerLegs,
            String providerFailureCategory,
            String expectedOutcome,
            List<String> expectedStopOrder,
            List<String> expectedRoles,
            Integer expectedTotalDistanceMeters,
            Integer expectedTotalDurationSeconds,
            Integer expectedExpiresAtPlusMinutes,
            String expectedErrorCode,
            String expectedHttpStatus,
            String expectedFailureCategoryPublic,
            Integer expectedInvalidInputOrder,
            List<String> requestedRestaurantIds,
            List<FixtureRestaurant> restaurants) {
    }
}
