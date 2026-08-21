package com.masiton.ai.application;

import java.math.BigDecimal;

import com.masiton.orchestration.application.port.in.AutoRegisterVerifiedContentUseCase;
import com.masiton.orchestration.application.port.in.DecomposeRegistrationUnitsUseCase.BoundCandidate;
import com.masiton.orchestration.application.port.in.ExecuteRegistrationUnitUseCase.CategoryDecision;
import com.masiton.orchestration.application.port.in.ExecuteRegistrationUnitUseCase.PlaceDecision;
import com.masiton.orchestration.application.port.in.VerifyAiContentCandidateUseCase.Evidence;
import com.masiton.orchestration.application.port.in.VerifyAiContentCandidateUseCase.EvidenceType;
import com.masiton.orchestration.application.port.in.VerifyAiContentCandidateUseCase.VisitEvidenceCandidate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Worker 자동 실행({@code RegistrationUnitAutoExecutionService})과 관리자 등록 단위 실행
 * ({@code RegistrationUnitCommandService})이 공유하는 변환 로직이다. {@code BoundCandidate}의
 * 방문 근거 JSON을 orchestration 계약 타입으로 옮기고, {@code ai_registration_unit}의
 * {@code place_decision}·{@code category_decision}·{@code reused_resources} JSON 문자열을 만든다.
 * 두 실행 경로가 같은 판정 결과를 같은 방식으로 직렬화하도록 로직을 한 곳에 둔다.
 */
final class RegistrationUnitJsonSupport {

    private RegistrationUnitJsonSupport() {
    }

    static String value(BoundCandidate candidate) {
        return candidate == null ? null : candidate.value();
    }

    static VisitEvidenceCandidate toVisitEvidence(BoundCandidate candidate) {
        if (candidate == null || candidate.value() == null) {
            return null;
        }
        JsonNode evidenceNode = candidate.evidence();
        String type = evidenceNode == null ? "UNKNOWN" : evidenceNode.path("type").asText("UNKNOWN");
        EvidenceType evidenceType = switch (type) {
            case "TIMESTAMP" -> EvidenceType.TIMESTAMP;
            case "TEXT_RANGE" -> EvidenceType.TEXT_RANGE;
            default -> EvidenceType.UNKNOWN;
        };
        Long startMs = longOrNull(evidenceNode, "startMs");
        Long endMs = longOrNull(evidenceNode, "endMs");
        Long startOffset = longOrNull(evidenceNode, "startOffset");
        Long endOffset = longOrNull(evidenceNode, "endOffset");
        String sourceHash = evidenceNode != null && evidenceNode.hasNonNull("sourceHash")
                ? evidenceNode.path("sourceHash").asText() : null;
        BigDecimal confidence = candidate.confidence();
        double confidenceValue = confidence == null ? -1 : confidence.doubleValue();
        return new VisitEvidenceCandidate(candidate.value(), confidenceValue,
                new Evidence(evidenceType, startMs, endMs, startOffset, endOffset, sourceHash));
    }

    static String placeDecisionJson(ObjectMapper objectMapper, PlaceDecision decision) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("searchQuery", decision.searchQuery());
        node.put("kakaoPlaceId", decision.kakaoPlaceId());
        node.put("kakaoPlaceUrl", decision.kakaoPlaceUrl());
        node.put("roadAddress", decision.roadAddress());
        node.put("matchedBy", decision.matchedBy());
        return json(objectMapper, node);
    }

    static String categoryDecisionJson(ObjectMapper objectMapper, CategoryDecision decision) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("foodCategoryId", decision.foodCategoryId().toString());
        node.put("foodCategoryName", decision.foodCategoryName());
        node.put("resolvedBy", decision.resolvedBy());
        if (decision.matchedMappingId() == null) {
            node.putNull("matchedMappingId");
        } else {
            node.put("matchedMappingId", decision.matchedMappingId().toString());
        }
        return json(objectMapper, node);
    }

    static String reusedResourcesJson(ObjectMapper objectMapper,
                                      AutoRegisterVerifiedContentUseCase.RegistrationResult registration) {
        ArrayNode array = objectMapper.createArrayNode();
        if (!registration.creatorCreated()) {
            array.add("creator");
        }
        if (!registration.videoCreated()) {
            array.add("video");
        }
        return json(objectMapper, array);
    }

    private static Long longOrNull(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.path(field).asLong() : null;
    }

    private static String json(ObjectMapper objectMapper, JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            throw new IllegalStateException("Registration unit decision could not be serialized.", exception);
        }
    }
}
