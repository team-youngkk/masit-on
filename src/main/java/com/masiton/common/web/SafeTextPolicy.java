package com.masiton.common.web;

/**
 * 사용자·관리자 입력 텍스트를 안전한 일반 텍스트로 제한한다.
 *
 * <p>NFR-SECURITY-006은 컬렉션 이름, 제보·신고 설명·근거 URL, 큐레이션 제목·설명, 관리자 검토 내용에 대해 "HTML·스크립트·
 * 제어 문자 등 실행성 또는 로그 위조 입력을 안전한 일반 텍스트로 처리"할 것을 요구한다. 차단 목록이 아니라
 * 꺾쇠와 제어 문자 자체를 거부하는 허용 방식이므로 `img onerror`, `svg onload` 같은 우회 입력도 함께 막힌다.
 *
 * <p>호출처: {@link com.masiton.personal.application.PersonalCollectionService},
 * {@link com.masiton.curation.application.AdminCurationService},
 * {@link com.masiton.participation.application.ParticipationService}
 *
 * <p>{@code AdminParticipationService.safeText}(관리자 검토 내용)는 길이 검사와 결합돼 있어 아직
 * 자체 구현이다. 거부 문자 집합을 바꿀 때 함께 갱신한다.
 *
 * <p>제어 문자를 거부하는 이유는 두 가지다. 로그 위조(개행 주입)를 막고, PostgreSQL이 문자열 파라미터의
 * NUL을 거부해 `500`으로 새는 경로를 `400 INVALID_FIELD_VALUE`로 되돌린다.
 */
public final class SafeTextPolicy {

    private SafeTextPolicy() {
    }

    /**
     * 앞뒤 공백을 제거한 값을 돌려주고, 실행성 문자나 제어 문자가 있으면
     * {@link ErrorCode#INVALID_FIELD_VALUE}로 거부한다. `null`은 빈 문자열로 본다.
     */
    public static String requireSafe(String raw, String field) {
        String value = raw == null ? "" : raw.trim();
        if (value.indexOf('<') >= 0 || value.indexOf('>') >= 0
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, field,
                    "실행성 문자열이나 제어 문자는 입력할 수 없습니다.");
        }
        return value;
    }
}
