package com.masiton.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.ai.application.port.out.*;
import com.masiton.ai.application.port.out.dto.*;
import com.masiton.ai.infrastructure.worker.AiExtractionWorkerProperties;
import tools.jackson.databind.ObjectMapper;

@DisplayName("AI 추출 워커 서비스")
class AiExtractionWorkerServiceTest {

    private final AiExtractionWorkerStore store = mock(AiExtractionWorkerStore.class);
    private final AiVideoExtractionProvider provider = mock(AiVideoExtractionProvider.class);
    private final AiExtractionResultProcessor processor = mock(AiExtractionResultProcessor.class);
    private final TemporaryInputCipher cipher = mock(TemporaryInputCipher.class);
    private final CapturingDelay delay = new CapturingDelay();
    private final AiExtractionWorkerProperties properties = properties();
    private final AiExtractionWorkerService service = new AiExtractionWorkerService(store, provider,
            Optional.of(processor), cipher, properties,
            Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC), delay);

    @Test
    @DisplayName("재시도 가능한 오류는 5초와 30초 대기 후 총 3회 시도한다")
    void poll_retryableErrors_세번시도하고정해진대기로재시도한다() {
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getApplicationQuotaLimit()).isEqualTo(10);
        when(store.quotaUsage(any(java.time.OffsetDateTime.class))).thenReturn(0L);
        doReturn(Optional.of(job(1))).when(store).claim(any(), any(), any(), anyInt(), any(), anyLong());
        when(store.beginRetry(any(), any(), any(), any(), anyInt(), any(), anyLong()))
                .thenReturn(Optional.of(2), Optional.of(3));
        when(store.recordRetryableFailure(any(), any(), anyInt(), any(), any(), anyString())).thenReturn(true);
        doThrow(new AiProviderException(AiProviderFailureCategory.TIMEOUT))
                .doThrow(new AiProviderException(AiProviderFailureCategory.RATE_LIMIT))
                .doThrow(new AiProviderException(AiProviderFailureCategory.UPSTREAM))
                .when(provider).extract(any());

        service.poll();

        verify(provider, times(3)).extract(any());
        verify(store).completeFailure(any(), any(), eq(3), any(), any(), eq("UPSTREAM"));
        assertThat(delay.delays).containsExactly(Duration.ofSeconds(5), Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("스키마 오류와 제공자 차단은 즉시 실패 처리한다")
    void poll_nonRetryableError_즉시실패하고재시도하지않는다() {
        when(store.quotaUsage(any(java.time.OffsetDateTime.class))).thenReturn(0L);
        doReturn(Optional.of(job(1))).when(store).claim(any(), any(), any(), anyInt(), any(), anyLong());
        when(provider.extract(any())).thenThrow(new AiProviderException(AiProviderFailureCategory.SCHEMA));

        service.poll();

        verify(provider).extract(any());
        verify(store).completeFailure(any(), any(), eq(1), any(), any(), eq("SCHEMA"));
        verify(store, never()).recordRetryableFailure(any(), any(), anyInt(), any(), any(), any());
        assertThat(delay.delays).isEmpty();
    }

    @Test
    @DisplayName("애플리케이션 쿼터 초과 시 제공자를 호출하지 않는다")
    void poll_quotaExceeded_클레임과제공자호출을중단한다() {
        when(store.quotaUsage(any())).thenReturn(properties.getApplicationQuotaLimit());

        service.poll();

        verify(store, never()).claim(any(), any(), any(), anyInt(), any(), anyLong());
        verifyNoInteractions(provider);
    }

    @Test
    @DisplayName("정상 결과는 결과 프로세서와 성공 완료를 호출한다")
    void poll_success_결과처리후성공완료한다() throws Exception {
        when(store.quotaUsage(any(java.time.OffsetDateTime.class))).thenReturn(0L);
        UUID id = UUID.randomUUID();
        doReturn(Optional.of(new AiExtractionWorkerStore.ClaimedJob(id,
                URI.create("https://www.youtube.com/watch?v=video-id"), null, 1)))
                .when(store).claim(any(), any(), any(), anyInt(), any(), anyLong());
        AiVideoExtractionResult result = new AiVideoExtractionResult(new ObjectMapper().readTree("{\"resultCompleteness\":\"COMPLETE\"}"), "req-1");
        when(provider.extract(any())).thenReturn(result);
        when(processor.process(eq(id), anyString(), eq(1), any(), any(), eq(result))).thenReturn(true);

        service.poll();

        verify(processor).process(eq(id), anyString(), eq(1), any(), any(), eq(result));
    }

    private static AiExtractionWorkerStore.ClaimedJob job(int attempt) {
        return new AiExtractionWorkerStore.ClaimedJob(UUID.randomUUID(), URI.create("https://www.youtube.com/watch?v=video-id"), null, attempt);
    }

    private static AiExtractionWorkerProperties properties() {
        AiExtractionWorkerProperties p = new AiExtractionWorkerProperties();
        p.setEnabled(true);
        p.setProviderQuotaLimit(100);
        p.setApplicationQuotaLimit(10);
        return p;
    }

    private static final class CapturingDelay implements AiWorkerDelay {
        private final java.util.List<Duration> delays = new java.util.ArrayList<>();
        @Override public boolean await(Duration duration) { delays.add(duration); return true; }
    }
}
