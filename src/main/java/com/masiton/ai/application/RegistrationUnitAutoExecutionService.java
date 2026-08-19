package com.masiton.ai.application;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.masiton.orchestration.application.port.in.AutoRegisterVerifiedContentUseCase;
import com.masiton.orchestration.application.port.in.DecomposeRegistrationUnitsUseCase;
import com.masiton.orchestration.application.port.in.DecomposeRegistrationUnitsUseCase.RegistrationUnitBundle;
import com.masiton.orchestration.application.port.in.ExecuteRegistrationUnitUseCase;
import com.masiton.orchestration.application.port.in.ExecuteRegistrationUnitUseCase.RegistrationUnitExecutionCommand;
import com.masiton.orchestration.application.port.in.ExecuteRegistrationUnitUseCase.RegistrationUnitExecutionResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Worker 자동 등록 경로에서 {@code BR-AIEXTRACT-001} 등록 단위마다
 * {@link ExecuteRegistrationUnitUseCase}로 {@code BR-AIEXTRACT-011} 5단계 검증을 모두 실행한다.
 * 등록 단위 분해({@code BR-AIEXTRACT-001})는 이 클래스가 소유하고, 판정·등록 자체는 orchestration의
 * 공개 use case에 위임한다.
 *
 * <p>{@link ExecuteRegistrationUnitUseCase#execute}는 외부 호출(Kakao·YouTube)을 포함하므로,
 * 호출자는 이 메서드를 DB 트랜잭션 밖에서 실행해야 한다.</p>
 */
@Service
class RegistrationUnitAutoExecutionService {

    private final DecomposeRegistrationUnitsUseCase decomposeRegistrationUnits;
    private final ExecuteRegistrationUnitUseCase executeRegistrationUnit;
    private final ObjectMapper objectMapper;

    RegistrationUnitAutoExecutionService(
            DecomposeRegistrationUnitsUseCase decomposeRegistrationUnits,
            ExecuteRegistrationUnitUseCase executeRegistrationUnit,
            ObjectMapper objectMapper) {
        this.decomposeRegistrationUnits = decomposeRegistrationUnits;
        this.executeRegistrationUnit = executeRegistrationUnit;
        this.objectMapper = objectMapper;
    }

    List<RegistrationUnitOutcome> execute(JsonNode candidateFields, JsonNode fieldConfidences, JsonNode evidence,
                                          String channelId, String videoId, URI videoUrl) {
        List<RegistrationUnitBundle> bundles = decomposeRegistrationUnits.decompose(
                new DecomposeRegistrationUnitsUseCase.DecomposeRegistrationUnitsCommand(
                        candidateFields, fieldConfidences, evidence));

        List<RegistrationUnitOutcome> outcomes = new ArrayList<>();
        for (RegistrationUnitBundle bundle : bundles) {
            String restaurantName = bundle.restaurantName().value();
            RegistrationUnitExecutionCommand command = new RegistrationUnitExecutionCommand(
                    restaurantName, RegistrationUnitJsonSupport.value(bundle.address()),
                    RegistrationUnitJsonSupport.value(bundle.menu()),
                    RegistrationUnitJsonSupport.toVisitEvidence(bundle.visitEvidence()), channelId, videoId, videoUrl);
            RegistrationUnitExecutionResult result = executeRegistrationUnit.execute(command);
            outcomes.add(toOutcome(bundle.unitIndex(), restaurantName, result));
        }
        return List.copyOf(outcomes);
    }

    private RegistrationUnitOutcome toOutcome(int unitIndex, String restaurantName,
                                              RegistrationUnitExecutionResult result) {
        if (!result.confirmed()) {
            return RegistrationUnitOutcome.blocked(unitIndex, restaurantName, result.blockReason());
        }
        AutoRegisterVerifiedContentUseCase.RegistrationResult registration = result.registration();
        return RegistrationUnitOutcome.confirmed(unitIndex, restaurantName,
                RegistrationUnitJsonSupport.placeDecisionJson(objectMapper, result.placeDecision()),
                RegistrationUnitJsonSupport.categoryDecisionJson(objectMapper, result.categoryDecision()),
                registration.restaurantId(), registration.creatorId(), registration.videoId(), registration.visitId(),
                RegistrationUnitJsonSupport.reusedResourcesJson(objectMapper, registration));
    }
}
