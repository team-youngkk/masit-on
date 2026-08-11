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

        assertThat(service.verify(command("직접 방문", timestamp()))).isEmpty();
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

        assertThat(service.verify(command("직접 방문", timestamp()))).isEmpty();
    }

    @Test
    @DisplayName("YouTube 필수 메타데이터가 누락되면 검증 결과를 만들지 않는다")
    void verify_유튜브필수메타데이터누락_검증결과를만들지않는다() {
        given(restaurantReference.resolve(anyString(), anyString(), any(), anyString()))
                .willReturn(Optional.of(restaurant()));
        given(videoVerification.resolve(any())).willReturn(Optional.of(new VerifiedVideo(
                "video-1", "channel-1", "", "https://img.youtube.com/vi/video-1/0.jpg",
                "채널", "https://www.youtube.com/watch?v=video-1", now(), now())));

        assertThat(service.verify(command("직접 방문", timestamp()))).isEmpty();
    }

    @Test
    @DisplayName("장소·YouTube·실제 방문 근거를 순서대로 통과하면 확정 결과를 조합한다")
    void verify_모든외부검증과실제방문근거통과_확정결과를조합한다() {
        given(restaurantReference.resolve(anyString(), anyString(), any(), anyString()))
                .willReturn(Optional.of(restaurant()));
        given(videoVerification.resolve(any())).willReturn(Optional.of(video()));

        var result = service.verify(command("직접 방문", timestamp()));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().visitEvidenceConfirmed()).isTrue();
        assertThat(result.orElseThrow().videoId()).isEqualTo("video-1");
        InOrder order = inOrder(restaurantReference, videoVerification);
        order.verify(restaurantReference).resolve(anyString(), anyString(), any(), anyString());
        order.verify(videoVerification).resolve(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"단순 언급", "방문 추천", "방문했을 것 같다"})
    @DisplayName("언급·추천·추정만 있는 방문 후보는 확정하지 않는다")
    void verify_언급추천추정후보_확정하지않는다(String value) {
        given(restaurantReference.resolve(anyString(), anyString(), any(), anyString()))
                .willReturn(Optional.of(restaurant()));
        given(videoVerification.resolve(any())).willReturn(Optional.of(video()));

        assertThat(service.verify(command(value, timestamp()))).isEmpty();
    }

    @Test
    @DisplayName("방문 근거 구간이 없거나 알 수 없으면 정식 등록 검증을 통과시키지 않는다")
    void verify_방문근거구간불충분_확정하지않는다() {
        given(restaurantReference.resolve(anyString(), anyString(), any(), anyString()))
                .willReturn(Optional.of(restaurant()));
        given(videoVerification.resolve(any())).willReturn(Optional.of(video()));

        assertThat(service.verify(command("직접 방문", new VerifyAiContentCandidateUseCase.Evidence(
                VerifyAiContentCandidateUseCase.EvidenceType.UNKNOWN, null, null, null, null, null)))).isEmpty();
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
