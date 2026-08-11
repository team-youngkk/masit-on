package com.masiton.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.ai.application.port.out.AiExtractionResultStore;
import com.masiton.ai.application.port.out.dto.AiVideoExtractionResult;
import com.masiton.restaurant.application.port.out.FoodCategoryRepositoryPort;
import com.masiton.restaurant.application.port.out.PlaceVerificationPort;
import com.masiton.restaurant.application.port.out.RegionRepositoryPort;
import com.masiton.restaurant.application.port.out.VerifiedPlace;
import com.masiton.restaurant.domain.model.FoodCategory;
import com.masiton.restaurant.domain.model.Region;
import com.masiton.video.application.port.in.ResolveVerifiedVideoUseCase;
import com.masiton.video.application.port.out.VerifiedVideo;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@DisplayName("AI 추출 결과 처리기")
class AiExtractionResultProcessorServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiExtractionResultStore resultStore = mock(AiExtractionResultStore.class);
    private final AiExtractionResultCommitService commitService = mock(AiExtractionResultCommitService.class);
    private final PlaceVerificationPort placeVerification = mock(PlaceVerificationPort.class);
    private final ResolveVerifiedVideoUseCase videoVerification = mock(ResolveVerifiedVideoUseCase.class);
    private final RegionRepositoryPort regionRepository = mock(RegionRepositoryPort.class);
    private final FoodCategoryRepositoryPort foodCategoryRepository = mock(FoodCategoryRepositoryPort.class);
    private final AiExtractionResultProcessorService processor = new AiExtractionResultProcessorService(
            resultStore, commitService, placeVerification, videoVerification, regionRepository,
            foodCategoryRepository, objectMapper);

    @Test
    @DisplayName("필수 후보와 외부 검증이 통과하면 등록 커밋으로 전달한다")
    void process_필수후보와외부검증통과_원자등록커밋으로전달한다() throws Exception {
        // Given
        UUID jobId = UUID.randomUUID();
        String workerId = "worker-1";
        OffsetDateTime finishedAt = OffsetDateTime.parse("2026-08-11T00:00:10Z");
        given(resultStore.lockProcessingJob(jobId, workerId, 1))
                .willReturn(Optional.of(new AiExtractionResultStore.ProcessingJob(
                        jobId, "channel-1", "video-1", URI.create("https://www.youtube.com/watch?v=video-1"))));
        given(videoVerification.resolve(any())).willReturn(Optional.of(new VerifiedVideo(
                "video-1", "channel-1", "영상 제목", "https://img.youtube.com/vi/video-1/0.jpg",
                "채널", "https://www.youtube.com/watch?v=video-1", finishedAt, finishedAt)));
        given(placeVerification.verify(eq("맛집"), eq(URI.create("https://place.map.kakao.com/123")), eq(null)))
                .willReturn(Optional.of(new VerifiedPlace(
                        "kakao-1", "맛집", "https://place.map.kakao.com/123",
                        "서울특별시 마포구 월드컵로 1", "02-1234-5678", BigDecimal.valueOf(126.9), BigDecimal.valueOf(37.5))));
        Region region = mock(Region.class);
        given(region.isActive()).willReturn(true);
        given(region.getId()).willReturn(UUID.randomUUID());
        given(regionRepository.findByName("마포구")).willReturn(Optional.of(region));
        FoodCategory category = mock(FoodCategory.class);
        given(category.isActive()).willReturn(true);
        given(category.getId()).willReturn(UUID.randomUUID());
        given(foodCategoryRepository.findByName("한식")).willReturn(Optional.of(category));
        given(resultStore.findTag("MENU_NAENGMYEON")).willReturn(Optional.of(
                new AiExtractionResultStore.TagDefinition(UUID.randomUUID(), "MENU_NAENGMYEON", "MENU", "냉면",
                        "[]", "ACTIVE")));
        given(commitService.persistConfirmed(any(), any())).willReturn(true);

        // When
        boolean processed = processor.process(jobId, workerId, 1, finishedAt.minusSeconds(5), finishedAt,
                result("""
                        {"resultCompleteness":"COMPLETE","candidates":[
                          {"field":"restaurantName","value":"맛집","confidence":0.95,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}},
                          {"field":"menu","value":"한식","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}},
                          {"field":"address","value":"서울특별시 마포구 월드컵로 1","confidence":0.90,"evidence":{"type":"TEXT_RANGE","startOffset":1,"endOffset":5,"sourceHash":"hash"}},
                          {"field":"location","value":"https://place.map.kakao.com/123","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}},
                          {"field":"visitEvidence","value":"방문함","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}},
                          {"field":"tag","candidateTagId":"tag-1","tagType":"MENU","rawLabel":"냉면","normalizedCode":"MENU_NAENGMYEON","label":"냉면","confidence":0.9,"evidence":{"type":"TIMESTAMP","startMs":2,"endMs":3}}
                        ],"missingFields":[]}
                        """));

        // Then
        assertThat(processed).isTrue();
        verify(commitService).persistConfirmed(any(), any());
        verify(placeVerification).verify(eq("맛집"), eq(URI.create("https://place.map.kakao.com/123")), eq(null));
    }

    @Test
    @DisplayName("필수 후보가 누락되면 외부 검증과 정식 등록을 호출하지 않는다")
    void process_필수후보누락_자동보류하고외부검증하지않는다() throws Exception {
        // Given
        UUID jobId = UUID.randomUUID();
        String workerId = "worker-1";
        OffsetDateTime finishedAt = OffsetDateTime.parse("2026-08-11T00:00:10Z");
        given(resultStore.lockProcessingJob(jobId, workerId, 1))
                .willReturn(Optional.of(new AiExtractionResultStore.ProcessingJob(
                        jobId, "channel-1", "video-1", URI.create("https://www.youtube.com/watch?v=video-1"))));
        given(commitService.persistBlocked(any())).willReturn(true);

        // When
        boolean processed = processor.process(jobId, workerId, 1, finishedAt.minusSeconds(5), finishedAt,
                result("""
                        {"resultCompleteness":"PARTIAL","candidates":[
                          {"field":"restaurantName","value":"맛집","confidence":0.95,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}}
                        ],"missingFields":["address","location","visitEvidence"]}
                        """));

        // Then
        assertThat(processed).isTrue();
        verify(commitService).persistBlocked(any());
        verifyNoInteractions(videoVerification, placeVerification, regionRepository, foodCategoryRepository);
    }

    private AiVideoExtractionResult result(String json) throws Exception {
        JsonNode payload = objectMapper.readTree(json);
        return new AiVideoExtractionResult(payload, "provider-request-1");
    }
}
