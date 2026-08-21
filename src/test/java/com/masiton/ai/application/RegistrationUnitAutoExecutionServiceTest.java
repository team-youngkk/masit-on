package com.masiton.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.orchestration.application.port.in.AutoRegisterVerifiedContentUseCase;
import com.masiton.orchestration.application.port.in.DecomposeRegistrationUnitsUseCase;
import com.masiton.orchestration.application.port.in.DecomposeRegistrationUnitsUseCase.BoundCandidate;
import com.masiton.orchestration.application.port.in.DecomposeRegistrationUnitsUseCase.RegistrationUnitBundle;
import com.masiton.orchestration.application.port.in.ExecuteRegistrationUnitUseCase;
import com.masiton.orchestration.application.port.in.ExecuteRegistrationUnitUseCase.CategoryDecision;
import com.masiton.orchestration.application.port.in.ExecuteRegistrationUnitUseCase.PlaceDecision;
import com.masiton.orchestration.application.port.in.ExecuteRegistrationUnitUseCase.RegistrationUnitExecutionCommand;
import com.masiton.orchestration.application.port.in.ExecuteRegistrationUnitUseCase.RegistrationUnitExecutionResult;

import tools.jackson.databind.ObjectMapper;

/**
 * Worker 자동 실행 경로가 등록 단위 분해({@code BR-AIEXTRACT-001})와 {@code BR-AIEXTRACT-011} 5단계
 * 판정({@code ExecuteRegistrationUnitUseCase})을 어떻게 조합해 {@code RegistrationUnitOutcome}으로
 * 옮기는지 검증한다. 5단계 판정 자체의 세부 규칙은 {@code RegistrationUnitExecutionServiceTest}가
 * 별도로 검증한다.
 */
@DisplayName("Worker 등록 단위 자동 실행")
class RegistrationUnitAutoExecutionServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DecomposeRegistrationUnitsUseCase decomposeRegistrationUnits =
            mock(DecomposeRegistrationUnitsUseCase.class);
    private final ExecuteRegistrationUnitUseCase executeRegistrationUnit = mock(ExecuteRegistrationUnitUseCase.class);
    private final RegistrationUnitAutoExecutionService service = new RegistrationUnitAutoExecutionService(
            decomposeRegistrationUnits, executeRegistrationUnit, objectMapper);
    private final URI videoUrl = URI.create("https://www.youtube.com/watch?v=video-1");

    @Test
    @DisplayName("5단계를 모두 통과하면 AUTO_CONFIRMED 결과를 등록 결과 4종과 함께 반환한다")
    void execute_5단계모두통과_AUTO_CONFIRMED결과를반환한다() {
        given(decomposeRegistrationUnits.decompose(any())).willReturn(List.of(
                bundle(1, "행복식당", "서울특별시 마포구 월드컵로 1", "냉면")));
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        UUID foodCategoryId = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();
        given(executeRegistrationUnit.execute(any())).willReturn(RegistrationUnitExecutionResult.confirmed(
                new PlaceDecision("행복식당", "kakao-1", "https://place.map.kakao.com/1", "서울특별시 마포구 월드컵로 1",
                        "NAME_CONTAINS_AND_DISTRICT_AND_CATEGORY"),
                new CategoryDecision(foodCategoryId, "한식", "KAKAO_PLACE_CATEGORY", mappingId),
                new AutoRegisterVerifiedContentUseCase.RegistrationResult(
                        restaurantId, creatorId, videoId, visitId, true, false, false, true)));

        List<RegistrationUnitOutcome> outcomes = service.execute(json("{}"), json("{}"), json("{}"),
                "channel-1", "video-1", videoUrl);

        assertThat(outcomes).hasSize(1);
        RegistrationUnitOutcome outcome = outcomes.get(0);
        assertThat(outcome.unitIndex()).isEqualTo(1);
        assertThat(outcome.restaurantName()).isEqualTo("행복식당");
        assertThat(outcome.reviewStatus()).isEqualTo("AUTO_CONFIRMED");
        assertThat(outcome.blockReason()).isNull();
        assertThat(outcome.registeredRestaurantId()).isEqualTo(restaurantId);
        assertThat(outcome.registeredCreatorId()).isEqualTo(creatorId);
        assertThat(outcome.registeredVideoId()).isEqualTo(videoId);
        assertThat(outcome.registeredVisitId()).isEqualTo(visitId);
        assertThat(outcome.reusedResourcesJson()).contains("creator", "video");
        assertThat(outcome.placeDecisionJson()).contains("행복식당", "kakao-1", "NAME_CONTAINS_AND_DISTRICT_AND_CATEGORY");
        assertThat(outcome.categoryDecisionJson()).contains("한식", "KAKAO_PLACE_CATEGORY", mappingId.toString());
    }

    @Test
    @DisplayName("장소 동일성을 확정하지 못하면 AUTO_BLOCKED/PLACE_NOT_FOUND를 반환한다")
    void execute_장소동일성실패_AUTO_BLOCKED_PLACE_NOT_FOUND를반환한다() {
        given(decomposeRegistrationUnits.decompose(any())).willReturn(List.of(
                bundle(1, "행복식당", "서울특별시 마포구 월드컵로 1", null)));
        given(executeRegistrationUnit.execute(any())).willReturn(
                RegistrationUnitExecutionResult.blocked("PLACE_NOT_FOUND"));

        List<RegistrationUnitOutcome> outcomes = service.execute(json("{}"), json("{}"), json("{}"),
                "channel-1", "video-1", videoUrl);

        assertThat(outcomes).hasSize(1);
        assertThat(outcomes.get(0).unitIndex()).isEqualTo(1);
        assertThat(outcomes.get(0).restaurantName()).isEqualTo("행복식당");
        assertThat(outcomes.get(0).blockReason()).isEqualTo("PLACE_NOT_FOUND");
        assertThat(outcomes.get(0).reviewStatus()).isEqualTo("AUTO_BLOCKED");
        assertThat(outcomes.get(0).registeredRestaurantId()).isNull();
    }

    @Test
    @DisplayName("여러 등록 단위 중 한 단위가 차단돼도 다른 단위의 확정 결과는 그대로 반환한다")
    void execute_복수등록단위_한단위차단_다른단위는확정결과를유지한다() {
        given(decomposeRegistrationUnits.decompose(any())).willReturn(List.of(
                bundle(1, "첫 맛집", "서울특별시 마포구 월드컵로 1", null),
                bundle(2, "둘째 맛집", "서울특별시 영등포구 여의대로 10", null)));
        UUID restaurantId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        UUID foodCategoryId = UUID.randomUUID();
        given(executeRegistrationUnit.execute(commandFor("첫 맛집"))).willReturn(RegistrationUnitExecutionResult.confirmed(
                new PlaceDecision("https://place.map.kakao.com/1", "서울특별시 마포구 월드컵로 1", "NAME_AND_DISTRICT"),
                new CategoryDecision(foodCategoryId, "한식", "KAKAO_PLACE_CATEGORY"),
                new AutoRegisterVerifiedContentUseCase.RegistrationResult(
                        restaurantId, creatorId, videoId, visitId, true, false, false, true)));
        given(executeRegistrationUnit.execute(commandFor("둘째 맛집")))
                .willReturn(RegistrationUnitExecutionResult.blocked("PLACE_AMBIGUOUS"));

        List<RegistrationUnitOutcome> outcomes = service.execute(json("{}"), json("{}"), json("{}"),
                "channel-1", "video-1", videoUrl);

        assertThat(outcomes).hasSize(2);
        assertThat(outcomes.get(0).restaurantName()).isEqualTo("첫 맛집");
        assertThat(outcomes.get(0).reviewStatus()).isEqualTo("AUTO_CONFIRMED");
        assertThat(outcomes.get(0).registeredRestaurantId()).isEqualTo(restaurantId);
        assertThat(outcomes.get(1).restaurantName()).isEqualTo("둘째 맛집");
        assertThat(outcomes.get(1).reviewStatus()).isEqualTo("AUTO_BLOCKED");
        assertThat(outcomes.get(1).blockReason()).isEqualTo("PLACE_AMBIGUOUS");
    }

    private RegistrationUnitExecutionCommand commandFor(String restaurantName) {
        return org.mockito.ArgumentMatchers.argThat(command -> command != null
                && restaurantName.equals(command.restaurantName()));
    }

    private RegistrationUnitBundle bundle(int unitIndex, String restaurantName, String address, String menu) {
        return new RegistrationUnitBundle(unitIndex, candidate(restaurantName), candidate(address), candidate(menu),
                null);
    }

    private BoundCandidate candidate(String value) {
        return value == null ? null : new BoundCandidate(value, null, null);
    }

    private tools.jackson.databind.JsonNode json(String value) {
        return objectMapper.readTree(value);
    }
}
