package com.masiton.ai.application;

import java.util.List;

/**
 * 관리자 AI 영상 추출 API 2.1절 "작업 최상위 {@code reviewStatus} 요약 규칙"의 6단계 우선순위를
 * 그대로 구현한 순수 함수다. 권위 있는 값은 {@code registrationUnits[].reviewStatus}이며, 이
 * 함수는 그 값들과 Snapshot 자체 판정값을 조합해 작업 최상위 요약값 하나를 계산한다.
 *
 * <p>이 클래스는 계산만 담당하고 조회·영속화에는 관여하지 않는다. 관리자 상세 조회 응답을
 * 만드는 호출자가 Snapshot과 {@code ai_registration_unit} 행을 조회한 뒤 이 함수를 호출한다.</p>
 */
public final class JobReviewStatusSummary {

    private JobReviewStatusSummary() {
    }

    /**
     * @param snapshotReviewStatus Snapshot 자체의 {@code review_status}. 아직 Snapshot이 없으면
     *                             {@code null}이며, 이때는 등록 단위 유무와 관계없이 {@code null}을
     *                             반환한다(순위 1: 자동 판정 전).
     * @param registrationUnitReviewStatuses 같은 Snapshot이 가진 {@code ai_registration_unit.review_status}
     *                                       전체. 등록 단위가 없으면 빈 목록이다.
     * @return 작업 최상위 {@code reviewStatus} 요약값
     */
    public static String summarize(String snapshotReviewStatus, List<String> registrationUnitReviewStatuses) {
        if (snapshotReviewStatus == null) {
            return null;
        }
        if (registrationUnitReviewStatuses == null || registrationUnitReviewStatuses.isEmpty()) {
            return snapshotReviewStatus;
        }
        if (registrationUnitReviewStatuses.contains("MANUAL_OVERRIDE")) {
            return "MANUAL_OVERRIDE";
        }
        if (registrationUnitReviewStatuses.contains("AUTO_BLOCKED")) {
            return "AUTO_BLOCKED";
        }
        if (registrationUnitReviewStatuses.contains("AUTO_CONFIRMED")) {
            return "AUTO_CONFIRMED";
        }
        return "AUTO_REJECTED";
    }
}
