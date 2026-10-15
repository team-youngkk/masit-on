package com.masiton.orchestration.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.masiton.orchestration.application.port.in.VerifyAiContentCandidateUseCase.Evidence;
import com.masiton.orchestration.application.port.in.VerifyAiContentCandidateUseCase.EvidenceType;
import com.masiton.orchestration.application.port.in.VerifyAiContentCandidateUseCase.VisitEvidenceCandidate;

@DisplayName("방문 근거 끝 문장부호 정규화")
class VisitEvidenceConfirmationTest {

    @ParameterizedTest
    @ValueSource(strings = {"제가 맛집을 방문했습니다!", "제가 맛집을 방문했습니다！！",
            "제가 맛집을 방문했습니다。!", "저희가 맛집에 다녀왔어요!", "채널이 맛집을 방문했습니다！"})
    @DisplayName("직접 방문 완료형은 끝의 느낌표 표기 차이로 차단하지 않는다")
    void 확인_완료형끝문장부호_방문근거를인정한다(String claim) {
        // Given
        var candidate = candidate(claim, timestamp());
        // When / Then
        assertThat(VisitEvidenceConfirmation.confirmsActualVisit(candidate, "맛집", "채널")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"제가 맛집을 방문했습니다?!", "제가 맛집을 방문했습니다？!",
            "제가 맛집을 안 방문했습니다!", "제가 맛집을 방문하지 않았습니다!",
            "제가 맛집을 방문할 예정입니다!", "맛집 방문을 추천합니다!",
            "친구가 맛집을 방문했습니다!", "제가 다른식당을 방문했습니다!",
            "제가 맛집을! 방문했습니다", "제가 맛집을 방문했을 것 같습니다!", "!", ""})
    @DisplayName("느낌표가 있어도 부정·의문·예정·타인·다른 장소·중간 문장부호는 거부한다")
    void 확인_방문확정불가문장_근거를거부한다(String claim) {
        // Given / When / Then
        assertThat(VisitEvidenceConfirmation.confirmsActualVisit(candidate(claim, timestamp()), "맛집", "채널"))
                .isFalse();
    }

    @Test
    @DisplayName("보완 텍스트와 알 수 없는 근거 및 잘못된 영상 시간은 느낌표와 무관하게 거부한다")
    void 확인_영상근거없음_근거를거부한다() {
        // Given
        String claim = "제가 맛집을 방문했습니다!";
        var text = new Evidence(EvidenceType.TEXT_RANGE, null, null, 0L, 20L, "fixture-hash");
        var unknown = new Evidence(EvidenceType.UNKNOWN, null, null, null, null, null);
        var invalid = new Evidence(EvidenceType.TIMESTAMP, 20L, 10L, null, null, null);
        // When / Then
        for (var evidence : new Evidence[] {text, unknown, invalid}) {
            assertThat(VisitEvidenceConfirmation.confirmsActualVisit(candidate(claim, evidence), "맛집", "채널"))
                    .isFalse();
        }
    }

    private VisitEvidenceCandidate candidate(String claim, Evidence evidence) {
        return new VisitEvidenceCandidate(claim, 0.9, evidence);
    }

    private Evidence timestamp() {
        return new Evidence(EvidenceType.TIMESTAMP, 0L, 1000L, null, null, null);
    }
}
