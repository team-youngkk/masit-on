package com.masiton.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.masiton.ai.application.port.out.AiExtractionResultStore;
import com.masiton.ai.application.port.out.AiExtractionResultProcessor;
import com.masiton.ai.application.port.out.AiRegistrationUnitRetryStore;
import com.masiton.ai.application.port.out.AiRegistrationUnitStore;
import com.masiton.ai.application.port.out.TemporaryInputCipher;
import com.masiton.ai.application.port.out.dto.AiVideoExtractionResult;
import com.masiton.orchestration.application.port.in.VerifyAiContentCandidateUseCase;
import com.masiton.video.application.port.in.ResolveVerifiedVideoUseCase;
import com.masiton.video.application.port.out.VerifiedVideo;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@DisplayName("AI 추출 결과 처리기")
class AiExtractionResultProcessorServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiExtractionResultStore resultStore = mock(AiExtractionResultStore.class);
    private final AiExtractionResultCommitService commitService = mock(AiExtractionResultCommitService.class);
    private final VerifyAiContentCandidateUseCase contentVerification = mock(VerifyAiContentCandidateUseCase.class);
    private final RegistrationUnitAutoExecutionService registrationUnitAutoExecution =
            mock(RegistrationUnitAutoExecutionService.class);
    private final AiExtractionResultProcessorService processor = new AiExtractionResultProcessorService(
            resultStore, commitService, contentVerification, registrationUnitAutoExecution, objectMapper);

    @Test
    @DisplayName("S3 단위 결과를 보존하고 기존 단일 등록 경로는 실행하지 않는다")
    void process_S3명시단위_단위실행결과만커밋한다() throws Exception {
        UUID jobId = UUID.randomUUID();
        OffsetDateTime finished = OffsetDateTime.parse("2026-10-14T00:00:10Z");
        given(resultStore.lockProcessingJob(jobId, "worker", 1)).willReturn(Optional.of(
                new AiExtractionResultStore.ProcessingJob(jobId, "channel", "video", URI.create("https://www.youtube.com/watch?v=video"))));
        given(registrationUnitAutoExecution.execute(any(), any(), any(), any(), any(), any()))
                .willReturn(List.of(RegistrationUnitOutcome.blocked(1, "식당A", "PLACE_UNRESOLVED")));
        given(commitService.persistBlocked(any())).willReturn(true);
        JsonNode payload;
        try (var input = getClass().getResourceAsStream("/ai/s3-two-restaurants.json")) {
            payload = objectMapper.readTree(input);
        }
        assertThat(processor.process(jobId, "worker", 1, finished.minusSeconds(5), finished,
                new AiVideoExtractionResult(payload, "request"))).isTrue();
        var command = forClass(AiExtractionResultCommitService.ProcessCommand.class);
        verify(commitService).persistBlocked(command.capture());
        assertThat(objectMapper.readTree(command.getValue().candidateFields()).at("/registrationUnits/0/candidateUnitId").asText())
                .isEqualTo("a");
        assertThat(objectMapper.readTree(command.getValue().candidateFields()).at("/registrationUnits/0/location/value").asText())
                .isEqualTo("서울 강남구");
        assertThat(command.getValue().resultCompleteness()).isEqualTo("COMPLETE");
        assertThat(command.getValue().reviewStatus()).isEqualTo("AUTO_BLOCKED");
        verifyNoInteractions(contentVerification);
        verify(commitService, never()).persistConfirmed(any(), any());
    }

    @Test
    @DisplayName("COMPLETE 선언의 필수 후보 누락은 서버 계산 누락과 PARTIAL을 함께 저장한다")
    void process_COMPLETE필수누락_PARTIAL과계산누락저장() throws Exception {
        // Given
        UUID jobId = UUID.randomUUID();
        OffsetDateTime finishedAt = OffsetDateTime.parse("2026-10-14T00:00:10Z");
        given(resultStore.lockProcessingJob(jobId, "worker-1", 1)).willReturn(Optional.of(
                new AiExtractionResultStore.ProcessingJob(jobId, "channel", "video", URI.create("https://youtu.be/video"))));
        given(commitService.persistBlocked(any())).willReturn(true);
        // When
        processor.process(jobId, "worker-1", 1, finishedAt.minusSeconds(5), finishedAt, result("""
                {"resultCompleteness":"COMPLETE","candidates":[
                  {"field":"restaurantName","value":"맛집","confidence":0.9,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}},
                  {"field":"visitEvidence","value":"직접 방문","confidence":0.9,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}}
                ],"missingFields":[]}
                """));
        // Then
        var command = forClass(AiExtractionResultCommitService.ProcessCommand.class);
        verify(commitService).persistBlocked(command.capture());
        assertThat(command.getValue().resultCompleteness()).isEqualTo("PARTIAL");
        assertThat(objectMapper.readTree(command.getValue().missingFields()).toString())
                .isEqualTo("[\"address\",\"location\"]");
        verifyNoInteractions(contentVerification);
    }

    {
        given(registrationUnitAutoExecution.execute(any(), any(), any(), any(), any(), any())).willReturn(List.of());
    }

    @Test
    @DisplayName("PARTIAL에서 태그만 누락돼도 필수 후보와 외부 검증을 통과하면 등록 커밋으로 전달한다")
    void process_PARTIAL태그만누락_원자등록커밋으로전달한다() throws Exception {
        // Given
        UUID jobId = UUID.randomUUID();
        String workerId = "worker-1";
        OffsetDateTime finishedAt = OffsetDateTime.parse("2026-08-11T00:00:10Z");
        given(resultStore.lockProcessingJob(jobId, workerId, 1))
                .willReturn(Optional.of(new AiExtractionResultStore.ProcessingJob(
                        jobId, "channel-1", "video-1", URI.create("https://www.youtube.com/watch?v=video-1"))));
        UUID regionId = UUID.randomUUID();
        UUID foodCategoryId = UUID.randomUUID();
        given(contentVerification.verify(any())).willReturn(VerifyAiContentCandidateUseCase.VerificationResult.verified(
                new VerifyAiContentCandidateUseCase.VerifiedContent(
                regionId, foodCategoryId, "맛집", "kakao-1", "https://place.map.kakao.com/123",
                "서울특별시 마포구 월드컵로 1", "02-1234-5678", java.math.BigDecimal.valueOf(126.9),
                java.math.BigDecimal.valueOf(37.5), "channel-1", "채널", "https://www.youtube.com/channel/channel-1",
                "video-1", "영상 제목", "https://www.youtube.com/watch?v=video-1",
                "https://img.youtube.com/vi/video-1/0.jpg", finishedAt, finishedAt)));
        given(commitService.persistConfirmed(any(), any())).willReturn(true);

        // When
        boolean processed = processor.process(jobId, workerId, 1, finishedAt.minusSeconds(5), finishedAt,
                result("""
                        {"resultCompleteness":"PARTIAL","candidates":[
                          {"field":"restaurantName","value":"맛집","confidence":0.95,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}},
                          {"field":"menu","value":"냉면","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}},
                          {"field":"address","value":"서울특별시 마포구 월드컵로 1","confidence":0.90,"evidence":{"type":"TEXT_RANGE","startOffset":1,"endOffset":5,"sourceHash":"hash"}},
                          {"field":"location","value":"https://place.map.kakao.com/123","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}},
                          {"field":"visitEvidence","value":"직접 방문","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}}
                        ],"missingFields":["tag"]}
                        """));

        // Then
        assertThat(processed).isTrue();
        verify(commitService).persistConfirmed(any(), any());
        var verificationCommand = forClass(VerifyAiContentCandidateUseCase.VerificationCommand.class);
        verify(contentVerification).verify(verificationCommand.capture());
        assertThat(verificationCommand.getValue().visitEvidence().value()).isEqualTo("직접 방문");
        assertThat(verificationCommand.getValue().visitEvidence().evidence().type())
                .isEqualTo(VerifyAiContentCandidateUseCase.EvidenceType.TIMESTAMP);
    }

    @Test
    @DisplayName("신규 태그 후보는 표시명과 같은 자기 별칭을 만들지 않는다")
    void process_신규태그후보_빈별칭으로커밋한다() throws Exception {
        UUID jobId = UUID.randomUUID();
        OffsetDateTime finishedAt = OffsetDateTime.parse("2026-08-11T00:00:10Z");
        given(resultStore.lockProcessingJob(jobId, "worker-1", 1))
                .willReturn(Optional.of(new AiExtractionResultStore.ProcessingJob(
                        jobId, "channel-1", "video-1", URI.create("https://www.youtube.com/watch?v=video-1"))));
        given(contentVerification.verify(any())).willReturn(VerifyAiContentCandidateUseCase.VerificationResult.verified(
                new VerifyAiContentCandidateUseCase.VerifiedContent(
                        UUID.randomUUID(), UUID.randomUUID(), "맛집", "kakao-1",
                        "https://place.map.kakao.com/123", "서울특별시 마포구 월드컵로 1", "02-1234-5678",
                        java.math.BigDecimal.valueOf(126.9), java.math.BigDecimal.valueOf(37.5),
                        "channel-1", "채널", "https://www.youtube.com/channel/channel-1", "video-1", "영상 제목",
                        "https://www.youtube.com/watch?v=video-1", "https://img.youtube.com/vi/video-1/0.jpg",
                        finishedAt, finishedAt)));
        given(resultStore.findTag("OCCASION_FAMILY")).willReturn(Optional.empty());
        given(commitService.persistConfirmed(any(), any())).willReturn(true);

        boolean processed = processor.process(jobId, "worker-1", 1, finishedAt.minusSeconds(5), finishedAt,
                result("""
                        {"resultCompleteness":"COMPLETE","candidates":[
                          {"field":"restaurantName","value":"맛집","confidence":0.95,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}},
                          {"field":"menu","value":"냉면","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}},
                          {"field":"address","value":"서울특별시 마포구 월드컵로 1","confidence":0.90,"evidence":{"type":"TEXT_RANGE","startOffset":1,"endOffset":5,"sourceHash":"hash"}},
                          {"field":"location","value":"https://place.map.kakao.com/123","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}},
                          {"field":"visitEvidence","value":"직접 방문","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}},
                          {"field":"tag","candidateTagId":"tag-family","tagType":"OCCASION","rawLabel":"가족 모임","normalizedCode":"OCCASION_FAMILY","label":"가족 모임","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}}
                        ],"missingFields":[]}
                        """));

        assertThat(processed).isTrue();
        var command = forClass(AiExtractionResultCommitService.ProcessCommand.class);
        verify(commitService).persistConfirmed(command.capture(), any());
        assertThat(command.getValue().resultCompleteness()).isEqualTo("COMPLETE");
        assertThat(command.getValue().tags()).singleElement()
                .satisfies(tag -> assertThat(tag.aliasesJson()).isEqualTo("[]"));
    }

    @Test
    @DisplayName("101자 AI 태그 원문은 저장 전에 정책 거부로 분류한다")
    void process_AI태그원문101자_정식저장대상에서제외한다() throws Exception {
        // Given
        UUID jobId = UUID.randomUUID();
        OffsetDateTime finishedAt = OffsetDateTime.parse("2026-08-11T00:00:10Z");
        String tooLongLabel = "가".repeat(101);
        given(resultStore.lockProcessingJob(jobId, "worker-1", 1))
                .willReturn(Optional.of(new AiExtractionResultStore.ProcessingJob(
                        jobId, "channel-1", "video-1", URI.create("https://www.youtube.com/watch?v=video-1"))));
        given(contentVerification.verify(any())).willReturn(VerifyAiContentCandidateUseCase.VerificationResult.verified(
                new VerifyAiContentCandidateUseCase.VerifiedContent(
                        UUID.randomUUID(), UUID.randomUUID(), "맛집", "kakao-1",
                        "https://place.map.kakao.com/123", "서울특별시 마포구 월드컵로 1", "02-1234-5678",
                        java.math.BigDecimal.valueOf(126.9), java.math.BigDecimal.valueOf(37.5),
                        "channel-1", "채널", "https://www.youtube.com/channel/channel-1", "video-1", "영상 제목",
                        "https://www.youtube.com/watch?v=video-1", "https://img.youtube.com/vi/video-1/0.jpg",
                        finishedAt, finishedAt)));
        given(resultStore.findTag("MENU_TOO_LONG")).willReturn(Optional.empty());
        given(commitService.persistConfirmed(any(), any())).willReturn(true);

        // When
        boolean processed = processor.process(jobId, "worker-1", 1, finishedAt.minusSeconds(5), finishedAt,
                result("""
                        {"resultCompleteness":"COMPLETE","candidates":[
                          {"field":"restaurantName","value":"맛집","confidence":0.95,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}},
                          {"field":"menu","value":"냉면","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}},
                          {"field":"address","value":"서울특별시 마포구 월드컵로 1","confidence":0.90,"evidence":{"type":"TEXT_RANGE","startOffset":1,"endOffset":5,"sourceHash":"hash"}},
                          {"field":"location","value":"https://place.map.kakao.com/123","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}},
                          {"field":"visitEvidence","value":"직접 방문","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}},
                          {"field":"tag","candidateTagId":"tag-too-long","tagType":"MENU","rawLabel":"%s","normalizedCode":"MENU_TOO_LONG","label":"%s","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}}
                        ],"missingFields":[]}
                        """.formatted(tooLongLabel, tooLongLabel)));

        // Then
        assertThat(processed).isTrue();
        var command = forClass(AiExtractionResultCommitService.ProcessCommand.class);
        verify(commitService).persistConfirmed(command.capture(), any());
        assertThat(command.getValue().tags()).singleElement().satisfies(tag -> {
            assertThat(tag.decision()).isEqualTo("AUTO_REJECT");
            assertThat(tag.reason()).isEqualTo("TAG_POLICY");
            assertThat(tag.autoConnectable()).isFalse();
        });
    }

    @Test
    @DisplayName("외부 검증이 실제 방문 근거를 확정하지 않으면 정식 등록을 호출하지 않는다")
    void process_외부방문근거미확정_정식등록하지않는다() throws Exception {
        // Given
        UUID jobId = UUID.randomUUID();
        OffsetDateTime finishedAt = OffsetDateTime.parse("2026-08-11T00:00:10Z");
        given(resultStore.lockProcessingJob(jobId, "worker-1", 1))
                .willReturn(Optional.of(new AiExtractionResultStore.ProcessingJob(
                        jobId, "channel-1", "video-1", URI.create("https://www.youtube.com/watch?v=video-1"))));
        given(contentVerification.verify(any())).willReturn(
                VerifyAiContentCandidateUseCase.VerificationResult.blocked("VISIT_EVIDENCE_REQUIRED"));
        given(commitService.persistBlocked(any())).willReturn(true);

        // When
        boolean processed = processor.process(jobId, "worker-1", 1, finishedAt.minusSeconds(5), finishedAt,
                result("""
                        {"resultCompleteness":"COMPLETE","candidates":[
                          {"field":"restaurantName","value":"맛집","confidence":0.95,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}},
                          {"field":"menu","value":"냉면","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}},
                          {"field":"address","value":"서울특별시 마포구 월드컵로 1","confidence":0.90,"evidence":{"type":"TEXT_RANGE","startOffset":1,"endOffset":5,"sourceHash":"hash"}},
                          {"field":"location","value":"https://place.map.kakao.com/123","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}},
                          {"field":"visitEvidence","value":"직접 방문","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}}
                        ],"missingFields":[]}
                        """));

        // Then
        assertThat(processed).isTrue();
        var blockedCommand = forClass(AiExtractionResultCommitService.ProcessCommand.class);
        verify(commitService).persistBlocked(blockedCommand.capture());
        assertThat(blockedCommand.getValue().blockReason()).isEqualTo("VISIT_EVIDENCE_REQUIRED");
        verify(commitService, never()).persistConfirmed(any(), any());
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
        verifyNoInteractions(contentVerification);
    }

    @Test
    @DisplayName("허용되지 않은 완결성 값은 PARTIAL로 정규화해 작업 CHECK 위반을 막는다")
    void process_허용되지않은완결성값_부분완료로정규화한다() throws Exception {
        // Given
        UUID jobId = UUID.randomUUID();
        String workerId = "worker-1";
        OffsetDateTime finishedAt = OffsetDateTime.parse("2026-08-11T00:00:10Z");
        given(resultStore.lockProcessingJob(jobId, workerId, 1))
                .willReturn(Optional.of(new AiExtractionResultStore.ProcessingJob(
                        jobId, "channel-1", "video-1", URI.create("https://www.youtube.com/watch?v=video-1"))));
        given(commitService.persistBlocked(any())).willReturn(true);

        // When
        processor.process(jobId, workerId, 1, finishedAt.minusSeconds(5), finishedAt,
                result("{\"resultCompleteness\":\"DONE\",\"candidates\":[],\"missingFields\":[]}"));

        // Then
        var command = forClass(AiExtractionResultCommitService.ProcessCommand.class);
        verify(commitService).persistBlocked(command.capture());
        assertThat(command.getValue().resultCompleteness()).isEqualTo("PARTIAL");
        assertThat(command.getValue().blockReason()).isEqualTo("INVALID_RESULT_COMPLETENESS");
    }

    @Test
    @DisplayName("같은 필드에 후보가 여러 개면 자동 보류하면서도 후보를 전부 Snapshot에 보존한다")
    void process_같은필드복수후보_자동보류하면서도후보를전부보존한다() throws Exception {
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
                        {"resultCompleteness":"COMPLETE","candidates":[
                          {"field":"restaurantName","value":"첫 맛집","confidence":0.95,"evidence":{"type":"TIMESTAMP","startMs":10,"endMs":20}},
                          {"field":"restaurantName","value":"둘째 맛집","confidence":0.94,"evidence":{"type":"TIMESTAMP","startMs":30,"endMs":40}},
                          {"field":"menu","value":"냉면","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}},
                          {"field":"address","value":"서울특별시 마포구 월드컵로 1","confidence":0.90,"evidence":{"type":"TEXT_RANGE","startOffset":1,"endOffset":5,"sourceHash":"hash"}},
                          {"field":"location","value":"https://place.map.kakao.com/123","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}},
                          {"field":"visitEvidence","value":"직접 방문","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":1,"endMs":2}}
                        ],"missingFields":[]}
                        """));

        // Then
        assertThat(processed).isTrue();
        var command = forClass(AiExtractionResultCommitService.ProcessCommand.class);
        verify(commitService).persistBlocked(command.capture());
        assertThat(command.getValue().blockReason()).isEqualTo("MULTIPLE_CANDIDATES");
        assertThat(command.getValue().reviewStatus()).isEqualTo("AUTO_BLOCKED");
        JsonNode candidateFields = objectMapper.readTree(command.getValue().candidateFields());
        assertThat(candidateFields.get("restaurantName").isArray()).isTrue();
        assertThat(candidateFields.get("restaurantName")).hasSize(2);
        assertThat(candidateFields.get("restaurantName").get(0).path("value").asText()).isEqualTo("첫 맛집");
        assertThat(candidateFields.get("restaurantName").get(1).path("value").asText()).isEqualTo("둘째 맛집");
        JsonNode fieldConfidences = objectMapper.readTree(command.getValue().fieldConfidences());
        JsonNode evidence = objectMapper.readTree(command.getValue().evidence());
        assertThat(fieldConfidences.has("restaurantName")).isFalse();
        assertThat(evidence.has("restaurantName")).isFalse();
        assertThat(candidateFields.get("menu").isTextual()).isTrue();
        assertThat(candidateFields.get("address").isTextual()).isTrue();
        assertThat(candidateFields.get("location").isTextual()).isTrue();
        assertThat(candidateFields.get("visitEvidence").isTextual()).isTrue();
        assertThat(fieldConfidences.has("menu")).isTrue();
        assertThat(fieldConfidences.has("address")).isTrue();
        assertThat(fieldConfidences.has("location")).isTrue();
        assertThat(fieldConfidences.has("visitEvidence")).isTrue();
        assertThat(evidence.has("menu")).isTrue();
        assertThat(evidence.has("address")).isTrue();
        assertThat(evidence.has("location")).isTrue();
        assertThat(evidence.has("visitEvidence")).isTrue();
        verifyNoInteractions(contentVerification);
    }

    @Test
    @DisplayName("보완 결과는 같은 candidateUnitId 단위만 교체하고 이미 확정된 단위는 그대로 보존한다")
    void process_보완결과_같은단위만교체하고확정단위를재실행하지않는다() throws Exception {
        UUID jobId = UUID.randomUUID();
        String workerId = "worker-1";
        UUID sourceSnapshotId = UUID.randomUUID();
        UUID resultSnapshotId = UUID.randomUUID();
        OffsetDateTime started = OffsetDateTime.parse("2026-08-11T00:00:00Z");
        OffsetDateTime finished = started.plusSeconds(5);
        AiExtractionResultStore store = resultStore;
        AiRegistrationUnitStore units = mock(AiRegistrationUnitStore.class);
        AiRegistrationUnitRetryStore retries = mock(AiRegistrationUnitRetryStore.class);
        TemporaryInputCipher cipher = mock(TemporaryInputCipher.class);
        ResolveVerifiedVideoUseCase videos = mock(ResolveVerifiedVideoUseCase.class);
        AiExtractionResultProcessorService staged = new AiExtractionResultProcessorService(
                store, commitService, contentVerification, registrationUnitAutoExecution, objectMapper,
                units, retries, cipher, videos, Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC));
        given(store.lockProcessingJob(jobId, workerId, 1)).willReturn(Optional.of(
                new AiExtractionResultStore.ProcessingJob(jobId, "channel-1", "video-1",
                        URI.create("https://www.youtube.com/watch?v=video-1"))));
        RegistrationUnitOutcome confirmed = RegistrationUnitOutcome.confirmed(1, "첫 식당",
                "{\"matchedBy\":\"NAME_AND_DISTRICT\"}", "{\"resolvedBy\":\"KAKAO_PLACE_CATEGORY\"}",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "[]");
        RegistrationUnitOutcome blocked = RegistrationUnitOutcome.blocked(2, "둘째 식당", "PLACE_NOT_FOUND");
        given(registrationUnitAutoExecution.execute(any(), any(), any(), any(), any(), any()))
                .willReturn(List.of(confirmed, blocked));
        given(registrationUnitAutoExecution.execute(any(), any(), any(), any(), any(), any(), eq(2)))
                .willReturn(List.of(RegistrationUnitOutcome.confirmed(2, "둘째 식당",
                        "{\"matchedBy\":\"MANUAL_OVERRIDE\"}", "{\"resolvedBy\":\"KAKAO_PLACE_CATEGORY\"}",
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "[]")));
        given(commitService.persistRemediationCheckpoint(any())).willReturn(sourceSnapshotId);
        given(commitService.persistRemediationResult(any(), any())).willReturn(true);
        given(store.latestSnapshotCandidate(jobId)).willReturn(Optional.of(new AiExtractionResultStore.SnapshotCandidate(
                resultSnapshotId, 2, "{}", "[]", "{}", "{}", "[]", false, "AUTO_CONFIRMED", null)));
        given(units.findBySnapshotId(sourceSnapshotId)).willReturn(List.of(
                row(sourceSnapshotId, 1, "첫 식당", "AUTO_CONFIRMED", null),
                row(sourceSnapshotId, 2, "둘째 식당", "AUTO_BLOCKED", "PLACE_NOT_FOUND")));
        given(videos.resolve(any())).willReturn(Optional.of(new VerifiedVideo("video-1", "channel-1",
                "영상 제목", "영상 설명", "thumb", "채널", "https://www.youtube.com/watch?v=video-1", started, finished)));
        given(cipher.encrypt(any())).willReturn(new TemporaryInputCipher.EncryptedInput(new byte[] {1}, "key-1"));
        UUID retryId = UUID.randomUUID();
        given(retries.claim(any())).willReturn(Optional.of(new AiRegistrationUnitRetryStore.RetryClaim(
                retryId, jobId, sourceSnapshotId, UUID.randomUUID(), "unit-b", "PLACE_NOT_FOUND", "[]", new byte[] {1},
                new byte[] {2}, "key-1", finished.plusHours(24), null, null, null, started, null)));
        AiVideoExtractionResult replacement = result(s3SingleUnitJson("unit-b", "둘째 식당", "https://place.map.kakao.com/2"));
        AiExtractionResultProcessor.SupplementResult supplement = new AiExtractionResultProcessor.SupplementResult(
                2, started.plusSeconds(1), started.plusSeconds(2), replacement, true);
        boolean processed = staged.process(jobId, workerId, 1, started, finished,
                result(s3UnitJson("unit-a", "첫 식당", "https://place.map.kakao.com/1")),
                (ignoredJobId, ignoredRetryId, ignoredSupplement) -> {
                    assertThat(ignoredSupplement).contains("candidateUnitId unit-b", "https://place.map.kakao.com/2");
                    return Optional.of(supplement);
                });

        assertThat(processed).isTrue();
        var command = forClass(AiExtractionResultCommitService.ProcessCommand.class);
        verify(commitService).persistRemediationResult(command.capture(), any());
        assertThat(command.getValue().registrationUnitOutcomes()).extracting(RegistrationUnitOutcome::unitIndex)
                .containsExactly(1, 2);
        assertThat(command.getValue().registrationUnitOutcomes().get(0)).isSameAs(confirmed);
        assertThat(command.getValue().registrationUnitOutcomes().get(1).reviewStatus()).isEqualTo("AUTO_CONFIRMED");
        verify(registrationUnitAutoExecution, times(1)).execute(any(), any(), any(), any(), any(), any());
        verify(registrationUnitAutoExecution).execute(any(), any(), any(), any(), any(), any(), eq(2));
    }

    @Test
    @DisplayName("예약만 된 보완 호출은 재시작 후 다시 호출하지 않고 실패 단위만 차단한다")
    void resumePending_예약후응답저장전재시작_Provider를재호출하지않는다() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID sourceUnitId = UUID.randomUUID();
        UUID retryId = UUID.randomUUID();
        String workerId = "worker-recovered";
        OffsetDateTime now = OffsetDateTime.parse("2026-08-11T00:00:00Z");
        AiRegistrationUnitStore units = mock(AiRegistrationUnitStore.class);
        AiRegistrationUnitRetryStore retries = mock(AiRegistrationUnitRetryStore.class);
        TemporaryInputCipher cipher = mock(TemporaryInputCipher.class);
        AiExtractionResultProcessorService recovering = new AiExtractionResultProcessorService(resultStore,
                commitService, contentVerification, registrationUnitAutoExecution, objectMapper, units, retries,
                cipher, mock(ResolveVerifiedVideoUseCase.class), Clock.fixed(now.toInstant(), ZoneOffset.UTC));
        String checkpoint = s3UnitJson("unit-a", "첫 식당", "https://place.map.kakao.com/1");
        AiRegistrationUnitStore.RegistrationUnitRow confirmedRow = row(snapshotId, 1, "첫 식당", "AUTO_CONFIRMED", null);
        AiRegistrationUnitStore.RegistrationUnitRow blockedRow = new AiRegistrationUnitStore.RegistrationUnitRow(
                sourceUnitId, snapshotId, 2, "둘째 식당", "AUTO_BLOCKED", "PLACE_NOT_FOUND", null, null,
                null, null, null, null, List.of(), "WORKER", now, null, null);
        given(resultStore.lockProcessingJob(jobId, workerId, 2)).willReturn(Optional.of(
                new AiExtractionResultStore.ProcessingJob(jobId, "channel", "video",
                        URI.create("https://www.youtube.com/watch?v=video"))));
        given(resultStore.latestSnapshotCandidate(jobId)).willReturn(Optional.of(new AiExtractionResultStore.SnapshotCandidate(
                snapshotId, 1, checkpoint, "[]", "{}", "{}", "[]", false, "AUTO_BLOCKED", null)));
        given(units.findBySnapshotId(snapshotId)).willReturn(List.of(confirmedRow, blockedRow));
        given(retries.findByJobAndCandidateUnitId(jobId, "unit-b")).willReturn(Optional.of(
                new AiRegistrationUnitRetryStore.RetryClaim(retryId, jobId, snapshotId, sourceUnitId, "unit-b",
                        "PLACE_NOT_FOUND", "[]", new byte[] {1}, new byte[] {2}, "key", now.plusHours(24),
                        2, "PENDING", null, now, null)));
        RegistrationUnitOutcome replacementOutcome = RegistrationUnitOutcome.blocked(2, "둘째 식당", "PLACE_NOT_FOUND");
        given(registrationUnitAutoExecution.execute(any(), any(), any(), any(), any(), any(), eq(2)))
                .willReturn(List.of(replacementOutcome));
        given(commitService.persistRemediationResult(any(), any())).willReturn(true);

        Optional<Boolean> resumed = recovering.resumePending(jobId, workerId, 2, now, now,
                (ignoredJob, actualRetry, input) -> { throw new AssertionError("reserved calls must not replay"); });

        assertThat(resumed).contains(true);
        verify(retries, never()).claim(any());
        verify(cipher, never()).decrypt(any());
        verify(commitService).persistRemediationResult(any(), any());
        verify(registrationUnitAutoExecution, never()).execute(any(), any(), any(), any(), any(), any());
        verify(commitService).persistRemediationResult(any(), any());
    }

    @Test
    @DisplayName("재시작 시 저장된 보완 응답을 재사용하고 Provider를 다시 호출하지 않는다")
    void resumePending_응답저장후재시작_저장된응답으로Snapshot을완성한다() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID sourceUnitId = UUID.randomUUID();
        UUID retryId = UUID.randomUUID();
        String workerId = "worker-recovered";
        OffsetDateTime now = OffsetDateTime.parse("2026-08-11T00:00:00Z");
        AiRegistrationUnitStore units = mock(AiRegistrationUnitStore.class);
        AiRegistrationUnitRetryStore retries = mock(AiRegistrationUnitRetryStore.class);
        TemporaryInputCipher cipher = mock(TemporaryInputCipher.class);
        AiExtractionResultProcessorService recovering = new AiExtractionResultProcessorService(resultStore,
                commitService, contentVerification, registrationUnitAutoExecution, objectMapper, units, retries,
                cipher, mock(ResolveVerifiedVideoUseCase.class), Clock.fixed(now.toInstant(), ZoneOffset.UTC));
        String checkpoint = s3UnitJson("unit-a", "첫 식당", "https://place.map.kakao.com/1");
        given(resultStore.lockProcessingJob(jobId, workerId, 2)).willReturn(Optional.of(
                new AiExtractionResultStore.ProcessingJob(jobId, "channel", "video",
                        URI.create("https://www.youtube.com/watch?v=video"))));
        given(resultStore.latestSnapshotCandidate(jobId)).willReturn(Optional.of(new AiExtractionResultStore.SnapshotCandidate(
                snapshotId, 1, checkpoint, "[]", "{}", "{}", "[]", false, "AUTO_BLOCKED", null)));
        given(units.findBySnapshotId(snapshotId)).willReturn(List.of(
                row(snapshotId, 1, "첫 식당", "AUTO_CONFIRMED", null),
                new AiRegistrationUnitStore.RegistrationUnitRow(sourceUnitId, snapshotId, 2, "둘째 식당",
                        "AUTO_BLOCKED", "PLACE_NOT_FOUND", null, null, null, null, null, null, List.of(),
                        "WORKER", now, null, null)));
        given(retries.findByJobAndCandidateUnitId(jobId, "unit-b")).willReturn(Optional.of(
                new AiRegistrationUnitRetryStore.RetryClaim(retryId, jobId, snapshotId, sourceUnitId, "unit-b",
                        "PLACE_NOT_FOUND", "[]", new byte[] {1}, new byte[] {2}, "input-key", now.plusHours(24),
                        2, "PENDING", null, now, null, new byte[] {3}, "result-key", now, "SUCCEEDED")));
        String savedResponse = "{\"providerRequestId\":\"supplement-request\",\"candidates\":"
                + s3SingleUnitJson("unit-b", "둘째 식당", "https://place.map.kakao.com/2") + "}";
        given(cipher.decrypt(any())).willReturn(savedResponse);
        given(registrationUnitAutoExecution.execute(any(), any(), any(), any(), any(), any(), eq(2)))
                .willReturn(List.of(RegistrationUnitOutcome.blocked(2, "둘째 식당", "PLACE_NOT_FOUND")));
        given(commitService.persistRemediationResult(any(), any())).willReturn(true);
        Optional<Boolean> resumed = recovering.resumePending(jobId, workerId, 2, now, now,
                (ignoredJob, ignoredRetry, ignoredInput) -> {
                    throw new AssertionError("saved Provider result must be reused");
                });

        assertThat(resumed).contains(true);
        var command = forClass(AiExtractionResultCommitService.ProcessCommand.class);
        verify(commitService).persistRemediationResult(command.capture(), any());
        assertThat(command.getValue().providerRequestId()).isEqualTo("supplement-request");
    }

    @Test
    @DisplayName("재시도 가능한 보완 실패 뒤 재시작하면 다음 제한된 시도를 실행한다")
    void resumePending_두번째보완시도실패후재시작_세번째Provider호출을허용한다() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID sourceUnitId = UUID.randomUUID();
        UUID retryId = UUID.randomUUID();
        String workerId = "worker-recovered";
        OffsetDateTime now = OffsetDateTime.parse("2026-08-11T00:00:00Z");
        AiRegistrationUnitStore units = mock(AiRegistrationUnitStore.class);
        AiRegistrationUnitRetryStore retries = mock(AiRegistrationUnitRetryStore.class);
        TemporaryInputCipher cipher = mock(TemporaryInputCipher.class);
        AiExtractionResultProcessorService recovering = new AiExtractionResultProcessorService(resultStore,
                commitService, contentVerification, registrationUnitAutoExecution, objectMapper, units, retries,
                cipher, mock(ResolveVerifiedVideoUseCase.class), Clock.fixed(now.toInstant(), ZoneOffset.UTC));
        String checkpoint = s3UnitJson("unit-a", "첫 식당", "https://place.map.kakao.com/1");
        given(resultStore.lockProcessingJob(jobId, workerId, 2)).willReturn(Optional.of(
                new AiExtractionResultStore.ProcessingJob(jobId, "channel", "video",
                        URI.create("https://www.youtube.com/watch?v=video"))));
        given(resultStore.latestSnapshotCandidate(jobId)).willReturn(Optional.of(new AiExtractionResultStore.SnapshotCandidate(
                snapshotId, 1, checkpoint, "[]", "{}", "{}", "[]", false, "AUTO_BLOCKED", null)));
        given(units.findBySnapshotId(snapshotId)).willReturn(List.of(
                row(snapshotId, 1, "첫 식당", "AUTO_CONFIRMED", null),
                new AiRegistrationUnitStore.RegistrationUnitRow(sourceUnitId, snapshotId, 2, "둘째 식당",
                        "AUTO_BLOCKED", "PLACE_NOT_FOUND", null, null, null, null, null, null, List.of(),
                        "WORKER", now, null, null)));
        given(retries.findByJobAndCandidateUnitId(jobId, "unit-b")).willReturn(Optional.of(
                new AiRegistrationUnitRetryStore.RetryClaim(retryId, jobId, snapshotId, sourceUnitId, "unit-b",
                        "PLACE_NOT_FOUND", "[]", new byte[] {1}, new byte[] {2}, "input-key", now.plusHours(24),
                        2, "PENDING", null, now, null, null, null, null, "FAILED")));
        given(cipher.decrypt(any())).willReturn("approved scoped input");
        given(registrationUnitAutoExecution.execute(any(), any(), any(), any(), any(), any(), eq(2)))
                .willReturn(List.of(RegistrationUnitOutcome.blocked(2, "둘째 식당", "PLACE_NOT_FOUND")));
        given(commitService.persistRemediationResult(any(), any())).willReturn(true);
        AiVideoExtractionResult thirdAttempt = result(
                s3SingleUnitJson("unit-b", "둘째 식당", "https://place.map.kakao.com/2"));

        Optional<Boolean> resumed = recovering.resumePending(jobId, workerId, 2, now, now,
                (ignoredJob, actualRetry, input) -> {
                    assertThat(actualRetry).isEqualTo(retryId);
                    assertThat(input).isEqualTo("approved scoped input");
                    return Optional.of(new AiExtractionResultProcessor.SupplementResult(3, now, now,
                            thirdAttempt, true));
                });

        assertThat(resumed).contains(true);
        verify(cipher).decrypt(any());
        verify(commitService).persistRemediationResult(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "wrong-unit", "duplicate-unit"})
    @DisplayName("malformed 보완 응답은 원본 단위와 확정 단위를 보존한다")
    void process_보완응답이누락되거나대상과다르면_원본단위를보존한다(String responseShape) throws Exception {
        UUID jobId = UUID.randomUUID();
        String workerId = "worker-1";
        UUID sourceSnapshotId = UUID.randomUUID();
        UUID resultSnapshotId = UUID.randomUUID();
        OffsetDateTime started = OffsetDateTime.parse("2026-08-11T00:00:00Z");
        OffsetDateTime finished = started.plusSeconds(5);
        AiRegistrationUnitStore units = mock(AiRegistrationUnitStore.class);
        AiRegistrationUnitRetryStore retries = mock(AiRegistrationUnitRetryStore.class);
        TemporaryInputCipher cipher = mock(TemporaryInputCipher.class);
        ResolveVerifiedVideoUseCase videos = mock(ResolveVerifiedVideoUseCase.class);
        AiExtractionResultProcessorService staged = new AiExtractionResultProcessorService(resultStore, commitService,
                contentVerification,
                registrationUnitAutoExecution, objectMapper, units, retries, cipher, videos,
                Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC));

        given(resultStore.lockProcessingJob(jobId, workerId, 1)).willReturn(Optional.of(
                new AiExtractionResultStore.ProcessingJob(jobId, "channel-1", "video-1",
                        URI.create("https://www.youtube.com/watch?v=video-1"))));
        RegistrationUnitOutcome confirmed = RegistrationUnitOutcome.confirmed(1, "첫 식당",
                "{\"matchedBy\":\"NAME_AND_DISTRICT\"}", "{\"resolvedBy\":\"KAKAO_PLACE_CATEGORY\"}",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "[]");
        RegistrationUnitOutcome blocked = RegistrationUnitOutcome.blocked(2, "둘째 식당", "PLACE_NOT_FOUND");
        given(registrationUnitAutoExecution.execute(any(), any(), any(), any(), any(), any()))
                .willReturn(List.of(confirmed, blocked));
        given(commitService.persistRemediationCheckpoint(any())).willReturn(sourceSnapshotId);
        given(commitService.persistRemediationResult(any(), any())).willReturn(true);
        given(resultStore.latestSnapshotCandidate(jobId)).willReturn(Optional.of(new AiExtractionResultStore.SnapshotCandidate(
                resultSnapshotId, 2, "{}", "[]", "{}", "{}", "[]", false, "AUTO_BLOCKED", null)));
        given(units.findBySnapshotId(sourceSnapshotId)).willReturn(List.of(
                row(sourceSnapshotId, 1, "첫 식당", "AUTO_CONFIRMED", null),
                row(sourceSnapshotId, 2, "둘째 식당", "AUTO_BLOCKED", "PLACE_NOT_FOUND")));
        given(videos.resolve(any())).willReturn(Optional.of(new VerifiedVideo("video-1", "channel-1",
                "영상 제목", "영상 설명", "thumb", "채널", "https://www.youtube.com/watch?v=video-1", started, finished)));
        given(cipher.encrypt(any())).willReturn(new TemporaryInputCipher.EncryptedInput(new byte[] {1}, "key-1"));
        UUID retryId = UUID.randomUUID();
        given(retries.claim(any())).willReturn(Optional.of(new AiRegistrationUnitRetryStore.RetryClaim(
                retryId, jobId, sourceSnapshotId, UUID.randomUUID(), "unit-b", "PLACE_NOT_FOUND", "[]", new byte[] {1},
                new byte[] {2}, "key-1", finished.plusHours(24), null, null, null, started, null)));
        AiVideoExtractionResult malformed = result(supplementJson(responseShape));

        boolean processed = staged.process(jobId, workerId, 1, started, finished,
                result(s3UnitJson("unit-a", "첫 식당", "https://place.map.kakao.com/1")),
                (ignoredJobId, ignoredRetryId, ignoredSupplement) -> Optional.of(
                        new AiExtractionResultProcessor.SupplementResult(2, started.plusSeconds(1),
                                started.plusSeconds(2), malformed, true)));

        assertThat(processed).isTrue();
        var command = forClass(AiExtractionResultCommitService.ProcessCommand.class);
        verify(commitService).persistRemediationResult(command.capture(), any());
        assertThat(command.getValue().registrationUnitOutcomes()).containsExactly(confirmed, blocked);
        assertThat(command.getValue().reviewStatus()).isEqualTo("AUTO_BLOCKED");
        verify(registrationUnitAutoExecution, times(1)).execute(any(), any(), any(), any(), any(), any());
        verify(registrationUnitAutoExecution, never()).execute(any(), any(), any(), any(), any(), any(), eq(2));
    }

    private AiRegistrationUnitStore.RegistrationUnitRow row(UUID snapshotId, int index, String name,
                                                              String status, String reason) {
        return new AiRegistrationUnitStore.RegistrationUnitRow(UUID.randomUUID(), snapshotId, index, name, status,
                reason, null, null, null, null, null, null, List.of(), "WORKER", OffsetDateTime.now(), null, null);
    }

    private String s3UnitJson(String firstId, String firstName, String firstLocation) {
        return "{\"resultCompleteness\":\"COMPLETE\",\"candidates\":[],\"missingFields\":[],"
                + "\"candidateTruncated\":false,\"registrationUnits\":["
                + unitJson(firstId, firstName, firstLocation) + ","
                + unitJson("unit-b", "둘째 식당", "https://place.map.kakao.com/2") + "]}";
    }

    private String s3SingleUnitJson(String id, String name, String location) {
        return "{\"resultCompleteness\":\"COMPLETE\",\"candidates\":[],\"missingFields\":[],"
                + "\"candidateTruncated\":false,\"registrationUnits\":["
                + unitJson(id, name, location) + "]}";
    }

    private String supplementJson(String shape) {
        return switch (shape) {
            case "missing" -> "{\"resultCompleteness\":\"COMPLETE\",\"candidates\":[]}";
            case "wrong-unit" -> s3SingleUnitJson("unit-other", "다른 식당", "https://place.map.kakao.com/9");
            case "duplicate-unit" -> "{\"resultCompleteness\":\"COMPLETE\",\"candidates\":[],"
                    + "\"missingFields\":[],\"candidateTruncated\":false,\"registrationUnits\":["
                    + unitJson("unit-b", "둘째 식당", "https://place.map.kakao.com/2") + ","
                    + unitJson("unit-b", "둘째 식당", "https://place.map.kakao.com/2") + "]}";
            default -> throw new IllegalArgumentException("unknown response shape: " + shape);
        };
    }

    private String unitJson(String id, String name, String location) {
        return "{\"candidateUnitId\":\"" + id + "\",\"restaurantName\":" + candidateJson(name)
                + ",\"address\":" + candidateJson("서울 마포구") + ",\"location\":" + candidateJson(location)
                + ",\"menus\":[" + candidateJson("냉면") + "],\"visitEvidence\":" + candidateJson("방문") + "}";
    }

    private String candidateJson(String value) {
        return "{\"value\":\"" + value + "\",\"confidence\":0.9,\"evidence\":{"
                + "\"type\":\"TIMESTAMP\",\"startMs\":1,\"endMs\":2}}";
    }

    private AiVideoExtractionResult result(String json) throws Exception {
        JsonNode payload = objectMapper.readTree(json);
        return new AiVideoExtractionResult(payload, "provider-request-1");
    }
}
