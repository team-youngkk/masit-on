package com.masiton.orchestration.application.port.in;

import java.math.BigDecimal;
import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * {@code BR-AIEXTRACT-001} 등록 단위 분해를 orchestration이 소유하는 공개 계약이다. 하나의
 * {@code ai_candidate_snapshot}이 가진 {@code candidate_fields}·{@code field_confidences}·
 * {@code evidence} 원본 JSON을 상호명 후보 하나와 그 후보에 결속된 주소·메뉴·방문 근거로 구성된
 * 장소 단위 등록 단위 목록으로 나눈다.
 */
public interface DecomposeRegistrationUnitsUseCase {

    List<RegistrationUnitBundle> decompose(DecomposeRegistrationUnitsCommand command);

    /**
     * 세 인자는 모두 데이터 계약 5절의 원본 저장 형태 그대로다. 필드 값이 후보 1건이면 문자열이고
     * {@code fieldConfidences}·{@code evidence}에 같은 키를 가지며, 2건 이상이면
     * {@code {value, confidence, evidence}} 항목의 배열이고 이때 {@code fieldConfidences}·
     * {@code evidence}는 그 필드 키를 갖지 않는다.
     */
    record DecomposeRegistrationUnitsCommand(JsonNode candidateFields, JsonNode fieldConfidences, JsonNode evidence) {
    }

    /**
     * {@code unitIndex}는 1부터 시작하며 {@code ai_registration_unit.unit_index}에 그대로 대응한다.
     * {@code address}·{@code menu}·{@code visitEvidence}는 이 단위에 결속된 후보가 없으면 {@code null}이다.
     */
    record RegistrationUnitBundle(
            int unitIndex,
            BoundCandidate restaurantName,
            BoundCandidate address,
            BoundCandidate menu,
            BoundCandidate visitEvidence) {
    }

    /** 원본 후보 값·신뢰도·근거를 그대로 옮긴 것이다. {@code confidence}는 신뢰도 정보가 없으면 {@code null}이다. */
    record BoundCandidate(String value, BigDecimal confidence, JsonNode evidence) {
    }
}
