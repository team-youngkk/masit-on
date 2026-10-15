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
     * S3에서는 {@code candidateFields.registrationUnits}의 명시적 단위 객체를 그대로 읽는다.
     * 나머지 두 인자는 S1/S2 이력용이다. S1/S2 필드 값이 후보 1건이면 문자열이고
     * {@code fieldConfidences}·{@code evidence}에 같은 키를 가지며, 2건 이상이면
     * {@code {value, confidence, evidence}} 항목의 배열이고 이때 {@code fieldConfidences}·
     * {@code evidence}는 그 필드 키를 갖지 않는다.
     */
    record DecomposeRegistrationUnitsCommand(JsonNode candidateFields, JsonNode fieldConfidences, JsonNode evidence) {
    }

    /**
     * {@code unitIndex}는 1부터 시작하며 {@code ai_registration_unit.unit_index}에 그대로 대응한다.
     * {@code address}·{@code location}·{@code visitEvidence}는 결속된 후보가 없으면 {@code null}이고,
     * {@code menus}는 빈 목록이다. {@code candidateUnitId}는 S3의 명시적 결속 식별자이며 S1/S2는 null이다.
     */
    record RegistrationUnitBundle(
            int unitIndex,
            String candidateUnitId,
            BoundCandidate restaurantName,
            BoundCandidate address,
            BoundCandidate location,
            List<BoundCandidate> menus,
            BoundCandidate visitEvidence) {
        public RegistrationUnitBundle(int unitIndex, BoundCandidate restaurantName, BoundCandidate address,
                                      BoundCandidate location, List<BoundCandidate> menus, BoundCandidate visitEvidence) {
            this(unitIndex, null, restaurantName, address, location, menus, visitEvidence);
        }
        public RegistrationUnitBundle(int unitIndex, BoundCandidate restaurantName, BoundCandidate address,
                                      BoundCandidate menu, BoundCandidate visitEvidence) {
            this(unitIndex, null, restaurantName, address, null, menu == null ? List.of() : List.of(menu), visitEvidence);
        }

        public BoundCandidate menu() {
            return menus.isEmpty() ? null : menus.get(0);
        }
    }

    /** 원본 후보 값·신뢰도·근거를 그대로 옮긴 것이다. {@code confidence}는 신뢰도 정보가 없으면 {@code null}이다. */
    record BoundCandidate(String value, BigDecimal confidence, JsonNode evidence) {
    }
}
