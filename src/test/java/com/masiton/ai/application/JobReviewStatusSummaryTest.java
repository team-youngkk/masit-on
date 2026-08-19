package com.masiton.ai.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("작업 최상위 reviewStatus 요약 규칙 (API 2.1절)")
class JobReviewStatusSummaryTest {

    @Test
    @DisplayName("순위1: Snapshot이 아직 없으면 등록 단위와 관계없이 null이다")
    void summarize_Snapshot없음_null이다() {
        assertThat(JobReviewStatusSummary.summarize(null, List.of())).isNull();
        assertThat(JobReviewStatusSummary.summarize(null, List.of("AUTO_CONFIRMED"))).isNull();
    }

    @Test
    @DisplayName("순위2: 등록 단위가 하나도 없으면 Snapshot 자체 판정값을 그대로 쓴다")
    void summarize_등록단위없음_Snapshot판정값을그대로쓴다() {
        assertThat(JobReviewStatusSummary.summarize("AUTO_BLOCKED", List.of())).isEqualTo("AUTO_BLOCKED");
        assertThat(JobReviewStatusSummary.summarize("AUTO_REJECTED", List.of())).isEqualTo("AUTO_REJECTED");
        assertThat(JobReviewStatusSummary.summarize("AUTO_BLOCKED", null)).isEqualTo("AUTO_BLOCKED");
    }

    @Test
    @DisplayName("순위3: MANUAL_OVERRIDE 단위가 하나라도 있으면 다른 조합과 관계없이 MANUAL_OVERRIDE다")
    void summarize_MANUAL_OVERRIDE포함_MANUAL_OVERRIDE다() {
        assertThat(JobReviewStatusSummary.summarize("AUTO_CONFIRMED",
                List.of("AUTO_CONFIRMED", "AUTO_BLOCKED", "AUTO_REJECTED", "MANUAL_OVERRIDE")))
                .isEqualTo("MANUAL_OVERRIDE");
        assertThat(JobReviewStatusSummary.summarize("AUTO_CONFIRMED", List.of("MANUAL_OVERRIDE")))
                .isEqualTo("MANUAL_OVERRIDE");
    }

    @Test
    @DisplayName("확정과 차단이 섞이면 처리할 예외가 남았다는 뜻으로 AUTO_BLOCKED다")
    void summarize_확정과차단조합_AUTO_BLOCKED다() {
        assertThat(JobReviewStatusSummary.summarize("AUTO_CONFIRMED", List.of("AUTO_CONFIRMED", "AUTO_BLOCKED")))
                .isEqualTo("AUTO_BLOCKED");
    }

    @Test
    @DisplayName("확정과 거부가 섞이면 거부는 종결이라 확정된 등록이 우선해 AUTO_CONFIRMED다")
    void summarize_확정과거부조합_AUTO_CONFIRMED다() {
        assertThat(JobReviewStatusSummary.summarize("AUTO_CONFIRMED", List.of("AUTO_CONFIRMED", "AUTO_REJECTED")))
                .isEqualTo("AUTO_CONFIRMED");
    }

    @Test
    @DisplayName("차단과 거부가 섞이면 처리할 예외가 남았다는 뜻으로 AUTO_BLOCKED다")
    void summarize_차단과거부조합_AUTO_BLOCKED다() {
        assertThat(JobReviewStatusSummary.summarize("AUTO_BLOCKED", List.of("AUTO_BLOCKED", "AUTO_REJECTED")))
                .isEqualTo("AUTO_BLOCKED");
    }

    @Test
    @DisplayName("확정+차단+거부가 모두 섞여도 AUTO_BLOCKED다")
    void summarize_확정차단거부모두조합_AUTO_BLOCKED다() {
        assertThat(JobReviewStatusSummary.summarize("AUTO_BLOCKED",
                List.of("AUTO_CONFIRMED", "AUTO_BLOCKED", "AUTO_REJECTED")))
                .isEqualTo("AUTO_BLOCKED");
    }

    @Test
    @DisplayName("순위6: 모든 단위가 AUTO_REJECTED면 AUTO_REJECTED다")
    void summarize_모든단위거부_AUTO_REJECTED다() {
        assertThat(JobReviewStatusSummary.summarize("AUTO_REJECTED", List.of("AUTO_REJECTED", "AUTO_REJECTED")))
                .isEqualTo("AUTO_REJECTED");
    }

    @Test
    @DisplayName("순위5: 확정 단위만 있으면 AUTO_CONFIRMED다")
    void summarize_확정단위만있으면_AUTO_CONFIRMED다() {
        assertThat(JobReviewStatusSummary.summarize("AUTO_CONFIRMED", List.of("AUTO_CONFIRMED")))
                .isEqualTo("AUTO_CONFIRMED");
    }
}
