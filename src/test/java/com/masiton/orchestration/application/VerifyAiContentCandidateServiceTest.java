package com.masiton.orchestration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

import com.masiton.orchestration.application.port.in.VerifyAiContentCandidateUseCase;
import com.masiton.restaurant.application.port.in.ResolveVerifiedRestaurantReferenceUseCase;
import com.masiton.video.application.port.out.VerifiedVideo;
import com.masiton.video.application.port.in.ResolveVerifiedVideoUseCase;

@DisplayName("AI 콘텐츠 검증 오케스트레이션")
class VerifyAiContentCandidateServiceTest {

    private final ResolveVerifiedRestaurantReferenceUseCase restaurantReference =
            mock(ResolveVerifiedRestaurantReferenceUseCase.class);
    private final ResolveVerifiedVideoUseCase videoVerification = mock(ResolveVerifiedVideoUseCase.class);
    private final VerifyAiContentCandidateService service = new VerifyAiContentCandidateService(
            restaurantReference, videoVerification);

    @Test
    @DisplayName("장소 검증이 실패하면 YouTube 검증을 호출하지 않는다")
    void verify_장소검증실패_유튜브검증을생략한다() {
        given(restaurantReference.resolve(anyString(), anyString(), any(), anyString()))
                .willReturn(Optional.empty());

        var result = service.verify(command("직접 방문", timestamp()));
        assertThat(result.isVerified()).isFalse();
        assertThat(result.failureReason()).isEqualTo("EXTERNAL_REFERENCE_MISMATCH");
        verifyNoInteractions(videoVerification);
    }

    @Test
    @DisplayName("YouTube 영상 또는 채널 식별자가 다르면 조합 결과를 만들지 않는다")
    void verify_유튜브식별자불일치_검증결과를만들지않는다() {
        given(restaurantReference.resolve(anyString(), anyString(), any(), anyString()))
                .willReturn(Optional.of(restaurant()));
        given(videoVerification.resolve(any())).willReturn(Optional.of(new VerifiedVideo(
                "other-video", "channel-1", "영상 제목", "https://img.youtube.com/vi/other/0.jpg",
                "채널", "https://www.youtube.com/watch?v=other-video", now(), now())));

        assertThat(service.verify(command("직접 방문", timestamp())).isVerified()).isFalse();
    }

    @Test
    @DisplayName("YouTube 필수 메타데이터가 누락되면 검증 결과를 만들지 않는다")
    void verify_유튜브필수메타데이터누락_검증결과를만들지않는다() {
        given(restaurantReference.resolve(anyString(), anyString(), any(), anyString()))
                .willReturn(Optional.of(restaurant()));
        given(videoVerification.resolve(any())).willReturn(Optional.of(new VerifiedVideo(
                "video-1", "channel-1", "", "https://img.youtube.com/vi/video-1/0.jpg",
                "채널", "https://www.youtube.com/watch?v=video-1", now(), now())));

        assertThat(service.verify(command("직접 방문", timestamp())).isVerified()).isFalse();
    }

    @Test
    @DisplayName("장소·YouTube·실제 방문 근거를 순서대로 통과하면 확정 결과를 조합한다")
    void verify_모든외부검증과실제방문근거통과_확정결과를조합한다() {
        given(restaurantReference.resolve(anyString(), anyString(), any(), anyString()))
                .willReturn(Optional.of(restaurant()));
        given(videoVerification.resolve(any())).willReturn(Optional.of(video()));

        var result = service.verify(command("제가 맛집을 직접 방문했습니다", timestamp()));

        assertThat(result.isVerified()).isTrue();
        assertThat(result.content().videoId()).isEqualTo("video-1");
        InOrder order = inOrder(restaurantReference, videoVerification);
        order.verify(restaurantReference).resolve(anyString(), anyString(), any(), anyString());
        order.verify(videoVerification).resolve(any());
    }

    @Test
    @DisplayName("형식이 유효해도 TEXT_RANGE 실제 방문 근거는 확정하지 않는다")
    void verify_유효한텍스트근거_확정하지않는다() {
        given(restaurantReference.resolve(anyString(), anyString(), any(), anyString()))
                .willReturn(Optional.of(restaurant()));
        given(videoVerification.resolve(any())).willReturn(Optional.of(video()));

        var result = service.verify(command("제가 맛집을 직접 방문했습니다", textRange()));

        assertThat(result.isVerified()).isFalse();
        assertThat(result.failureReason()).isEqualTo("VISIT_EVIDENCE_REQUIRED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"제가 맛집을 직접 방문했습니다.", "제가 맛집에 다녀왔습니다", "i 맛집 visited", "we 맛집 visited",
            "맛집을 방문했습니다", "이 맛집에 다녀왔습니다", "채널이 맛집을 방문했다"})
    @DisplayName("1인칭 또는 명시적 맛집 대상의 실제 방문 주장도 확정한다")
    void verify_자연스러운실제방문주장_확정결과를조합한다(String value) {
        given(restaurantReference.resolve(anyString(), anyString(), any(), anyString()))
                .willReturn(Optional.of(restaurant()));
        given(videoVerification.resolve(any())).willReturn(Optional.of(video()));

        assertThat(service.verify(command(value, timestamp())).isVerified()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"제가 맛집을 소개받고 다른 곳을 방문했습니다",
            "제가 맛집은 아니고 다른 곳을 방문했습니다",
            "제가 맛집을 언급한 뒤 다른 곳을 방문했습니다"})
    @DisplayName("앞 절에서만 언급된 맛집은 실제 방문 대상으로 확정하지 않는다")
    void verify_앞절에서만언급된맛집_방문대상으로확정하지않는다(String value) {
        given(restaurantReference.resolve(anyString(), anyString(), any(), anyString()))
                .willReturn(Optional.of(restaurant()));
        given(videoVerification.resolve(any())).willReturn(Optional.of(video()));

        assertThat(service.verify(command(value, timestamp())).isVerified()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"방문", "단순 언급", "방문 추천", "방문했을 것 같다", "직접 방문하지 않았습니다",
            "직접 방문했을까요?", "방문할 예정입니다", "방문함", "친구가 방문했습니다",
            "다른 사람이 다녀왔습니다", "유명인이 직접 방문했습니다", "친구가 맛집을 방문했습니다",
            "회사에 다녀왔습니다", "직접 안 방문했습니다", "직접 못 다녀왔습니다",
            "제가 안 방문했습니다", "제가 못 다녀왔습니다", "저는 안 방문했어요",
            "제가 맛집에 안 들렀습니다", "제가 맛집에 못 들렀어요",
            "impostor 맛집 visited", "weird 맛집 wentthere",
            "제가 맛집이 아닌 곳을 방문했습니다", "제가 맛집이 아니라 다른 곳에 다녀왔습니다",
            "제가 맛집을 제외하고 다른 곳을 방문했습니다",
            "저는 진짜 이집이 맛있어서 직접 방문했습니다", "맛집을 주문했다",
            "맛집에서 배달 주문했다", "맛집에서 포장 구매했다", "맛집 음식을 먹었다",
            "맛집에서 친구가 방문했다", "채가 맛집을 방문했다", "제가 맛집을 방문하고 싶었습니다",
            "맛집에서 주문하려고 합니다"})
    @DisplayName("언급·추천·추정·부정·의문·가정 방문 후보는 확정하지 않는다")
    void verify_확정할수없는방문후보_확정하지않는다(String value) {
        given(restaurantReference.resolve(anyString(), anyString(), any(), anyString()))
                .willReturn(Optional.of(restaurant()));
        given(videoVerification.resolve(any())).willReturn(Optional.of(video()));

        assertThat(service.verify(command(value, timestamp())).isVerified()).isFalse();
    }

    @Test
    @DisplayName("방문 근거 구간이 없거나 알 수 없으면 정식 등록 검증을 통과시키지 않는다")
    void verify_방문근거구간불충분_확정하지않는다() {
        given(restaurantReference.resolve(anyString(), anyString(), any(), anyString()))
                .willReturn(Optional.of(restaurant()));
        given(videoVerification.resolve(any())).willReturn(Optional.of(video()));

        var result = service.verify(command("직접 방문", new VerifyAiContentCandidateUseCase.Evidence(
                VerifyAiContentCandidateUseCase.EvidenceType.UNKNOWN, null, null, null, null, null)));
        assertThat(result.isVerified()).isFalse();
        assertThat(result.failureReason()).isEqualTo("VISIT_EVIDENCE_REQUIRED");
    }

    @Test
    @DisplayName("TEXT_RANGE의 범위나 source hash가 불완전하면 확정하지 않는다")
    void verify_텍스트근거범위불완전_확정하지않는다() {
        given(restaurantReference.resolve(anyString(), anyString(), any(), anyString()))
                .willReturn(Optional.of(restaurant()));
        given(videoVerification.resolve(any())).willReturn(Optional.of(video()));

        assertThat(service.verify(command("직접 방문", new VerifyAiContentCandidateUseCase.Evidence(
                VerifyAiContentCandidateUseCase.EvidenceType.TEXT_RANGE, null, null, 10L, 9L, "hash"))).isVerified())
                .isFalse();
        assertThat(service.verify(command("직접 방문", new VerifyAiContentCandidateUseCase.Evidence(
                VerifyAiContentCandidateUseCase.EvidenceType.TEXT_RANGE, null, null, 10L, 20L, ""))).isVerified())
                .isFalse();
    }

    private VerifyAiContentCandidateUseCase.VerificationCommand command(
            String evidenceValue, VerifyAiContentCandidateUseCase.Evidence evidence) {
        return new VerifyAiContentCandidateUseCase.VerificationCommand(
                "channel-1", "video-1", URI.create("https://www.youtube.com/watch?v=video-1"),
                "맛집", "서울특별시 마포구 월드컵로 1", URI.create("https://place.map.kakao.com/123"),
                "냉면", new VerifyAiContentCandidateUseCase.VisitEvidenceCandidate(
                        evidenceValue, 0.95, evidence));
    }

    private VerifyAiContentCandidateUseCase.Evidence timestamp() {
        return new VerifyAiContentCandidateUseCase.Evidence(
                VerifyAiContentCandidateUseCase.EvidenceType.TIMESTAMP, 100L, 200L, null, null, null);
    }

    private VerifyAiContentCandidateUseCase.Evidence textRange() {
        return new VerifyAiContentCandidateUseCase.Evidence(
                VerifyAiContentCandidateUseCase.EvidenceType.TEXT_RANGE, null, null, 10L, 20L, "hash-1");
    }

    private ResolveVerifiedRestaurantReferenceUseCase.VerifiedRestaurantReference restaurant() {
        return new ResolveVerifiedRestaurantReferenceUseCase.VerifiedRestaurantReference(
                UUID.randomUUID(), UUID.randomUUID(), "맛집", "kakao-1", "https://place.map.kakao.com/123",
                "서울특별시 마포구 월드컵로 1", "02-1234-5678", BigDecimal.valueOf(126.9), BigDecimal.valueOf(37.5));
    }

    private VerifiedVideo video() {
        return new VerifiedVideo("video-1", "channel-1", "영상 제목", "https://img.youtube.com/vi/video-1/0.jpg",
                "채널", "https://www.youtube.com/watch?v=video-1", now(), now());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.parse("2026-08-11T00:00:10Z");
    }
}
