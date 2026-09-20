package com.masiton.ai.presentation;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.masiton.ai.application.port.in.YoutubeChannelWatchManagementUseCase;
import com.masiton.ai.application.port.in.YoutubeChannelBackfillUseCase;
import com.masiton.common.observability.TraceIdFilter;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.GlobalExceptionHandler;

@DisplayName("관리자 YouTube 채널 감시 Controller API")
class AdminYoutubeChannelWatchControllerApiTest {

    private final YoutubeChannelWatchManagementUseCase useCase = mock(YoutubeChannelWatchManagementUseCase.class);
    private final YoutubeChannelBackfillUseCase backfill = mock(YoutubeChannelBackfillUseCase.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AdminYoutubeChannelWatchController(useCase, backfill))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new TraceIdFilter())
            .build();

    @Test
    @DisplayName("백필 접수는 외부 호출 없이 새 run을 202로 반환한다")
    void 백필접수_새run_202을반환한다() throws Exception {
        UUID creatorId = UUID.randomUUID(); UUID runId = UUID.randomUUID();
        when(backfill.start(creatorId)).thenReturn(new YoutubeChannelBackfillUseCase.StartResult(runId, "QUEUED", false));
        mockMvc.perform(post("/api/admin/ai/youtube-channel-watches/{creatorId}/backfills", creatorId))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.runId").value(runId.toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    @DisplayName("활성 백필 재접수는 기존 run을 200으로 반환한다")
    void 백필접수_활성run중복_200을반환한다() throws Exception {
        UUID creatorId = UUID.randomUUID(); UUID runId = UUID.randomUUID();
        when(backfill.start(creatorId)).thenReturn(new YoutubeChannelBackfillUseCase.StartResult(runId, "RUNNING", true));
        mockMvc.perform(post("/api/admin/ai/youtube-channel-watches/{creatorId}/backfills", creatorId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.runId").value(runId.toString()));
    }

    @Test
    @DisplayName("백필 중지 요청은 run 소유자 기준으로 204를 반환한다")
    void 백필중지_정상요청_204를반환한다() throws Exception {
        UUID creatorId = UUID.randomUUID(); UUID runId = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/ai/youtube-channel-watches/{creatorId}/backfills/{runId}/stop",
                        creatorId, runId))
                .andExpect(status().isNoContent());

        verify(backfill).stop(creatorId, runId);
    }

    @Test
    @DisplayName("백필 상태 조회는 안전한 집계와 오류 범주를 반환한다")
    void 백필상태조회_정상run_집계와오류범주를반환한다() throws Exception {
        UUID creatorId = UUID.randomUUID(); UUID runId = UUID.randomUUID();
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-09-15T00:00:00Z");
        when(backfill.get(creatorId, runId)).thenReturn(new YoutubeChannelBackfillUseCase.RunStatus(
                runId, "FAILED", 50, 40, 10, "YOUTUBE_RATE_LIMIT", timestamp, timestamp));

        mockMvc.perform(get("/api/admin/ai/youtube-channel-watches/{creatorId}/backfills/{runId}", creatorId, runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId.toString()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.scannedCount").value(50))
                .andExpect(jsonPath("$.submittedCount").value(40))
                .andExpect(jsonPath("$.reusedCount").value(10))
                .andExpect(jsonPath("$.lastErrorCategory").value("YOUTUBE_RATE_LIMIT"));
    }

    @Test
    @DisplayName("다른 Creator의 백필 run 조회는 404와 traceId를 반환한다")
    void 백필상태조회_소유자불일치_404와traceId를반환한다() throws Exception {
        UUID creatorId = UUID.randomUUID(); UUID runId = UUID.randomUUID();
        when(backfill.get(creatorId, runId)).thenThrow(new BusinessException(
                HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "요청한 자원을 찾을 수 없습니다."));

        mockMvc.perform(get("/api/admin/ai/youtube-channel-watches/{creatorId}/backfills/{runId}", creatorId, runId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("감시 목록은 여러 유튜버와 각 상태를 한 번에 반환한다")
    void 감시목록조회_여러유튜버_각상태를한번에반환한다() throws Exception {
        UUID creatorId = UUID.randomUUID();
        when(useCase.getStatuses(1, 20)).thenReturn(new YoutubeChannelWatchManagementUseCase.WatchPage(
                List.of(new YoutubeChannelWatchManagementUseCase.WatchSummary(
                        creatorId, "맛집 채널", true, true,
                        new YoutubeChannelWatchManagementUseCase.WatchStatus(
                                true, "ACTIVE", OffsetDateTime.parse("2026-08-12T01:02:03Z"), null, null))),
                1, 20, 1, 1, false));

        mockMvc.perform(get("/api/admin/ai/youtube-channel-watches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].creatorId").value(creatorId.toString()))
                .andExpect(jsonPath("$.items[0].channelName").value("맛집 채널"))
                .andExpect(jsonPath("$.items[0].status.subscriptionStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        verify(useCase).getStatuses(1, 20);
    }

    @Test
    @DisplayName("page가 0이면 400 INVALID_FIELD_VALUE와 page 오류를 반환하고 조회하지 않는다")
    void 감시목록조회_page가0이면_400INVALID_FIELD_VALUE와page오류를반환하고조회하지않는다() throws Exception {
        mockMvc.perform(get("/api/admin/ai/youtube-channel-watches")
                        .param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].field").value("page"))
                .andExpect(jsonPath("$.errors[0].reason").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        verify(useCase, never()).getStatuses(anyInt(), anyInt());
    }

    @Test
    @DisplayName("허용되지 않은 size가 15이면 400 INVALID_FIELD_VALUE와 size 오류를 반환하고 조회하지 않는다")
    void 감시목록조회_허용되지않은size가15이면_400INVALID_FIELD_VALUE와size오류를반환하고조회하지않는다() throws Exception {
        mockMvc.perform(get("/api/admin/ai/youtube-channel-watches")
                        .param("size", "15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].field").value("size"))
                .andExpect(jsonPath("$.errors[0].reason").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        verify(useCase, never()).getStatuses(anyInt(), anyInt());
    }

    @Test
    @DisplayName("활성화 요청은 감시 상태와 nullable 메타데이터를 반환한다")
    void 감시설정_활성화요청_상태와nullable메타데이터를반환한다() throws Exception {
        UUID creatorId = UUID.randomUUID();
        when(useCase.setEnabled(creatorId, true)).thenReturn(new YoutubeChannelWatchManagementUseCase.WatchStatus(
                true, "UNKNOWN", OffsetDateTime.parse("2026-08-12T01:00:00Z"), null, null));

        mockMvc.perform(put("/api/admin/ai/youtube-channel-watches/{creatorId}", creatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.subscriptionStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.lastNotificationAt").value("2026-08-12T01:00:00Z"))
                .andExpect(jsonPath("$.lastRenewedAt").isEmpty())
                .andExpect(jsonPath("$.lastErrorCategory").isEmpty());

        verify(useCase).setEnabled(creatorId, true);
    }

    @Test
    @DisplayName("enabled 필드가 없으면 400 MISSING_REQUIRED_FIELD를 반환한다")
    void 감시설정_enabled누락_400MISSING_REQUIRED_FIELD를반환한다() throws Exception {
        mockMvc.perform(put("/api/admin/ai/youtube-channel-watches/{creatorId}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUIRED_FIELD"));
    }

    @Test
    @DisplayName("잘못된 JSON 본문은 전역 framework 오류 계약으로 400을 반환한다")
    void 감시설정_잘못된JSON본문_전역framework오류계약으로400을반환한다() throws Exception {
        mockMvc.perform(put("/api/admin/ai/youtube-channel-watches/{creatorId}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("감시 조회에 Watch가 없으면 비활성 초기 상태를 반환한다")
    void 감시조회_Watch없음_비활성초기상태를반환한다() throws Exception {
        UUID creatorId = UUID.randomUUID();
        when(useCase.getStatus(creatorId)).thenReturn(new YoutubeChannelWatchManagementUseCase.WatchStatus(
                false, "INACTIVE", null, null, null));

        mockMvc.perform(get("/api/admin/ai/youtube-channel-watches/{creatorId}", creatorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.subscriptionStatus").value("INACTIVE"))
                .andExpect(jsonPath("$.lastNotificationAt").isEmpty())
                .andExpect(jsonPath("$.lastRenewedAt").isEmpty())
                .andExpect(jsonPath("$.lastErrorCategory").isEmpty())
                .andExpect(jsonPath("$.subscriptionTokenHash").doesNotExist())
                .andExpect(jsonPath("$.youtubeChannelId").doesNotExist());
    }

    @Test
    @DisplayName("UNKNOWN 감시 상태를 저장된 메타데이터와 함께 반환한다")
    void 감시조회_UNKNOWN상태_저장된메타데이터를반환한다() throws Exception {
        UUID creatorId = UUID.randomUUID();
        when(useCase.getStatus(creatorId)).thenReturn(new YoutubeChannelWatchManagementUseCase.WatchStatus(
                true, "UNKNOWN", OffsetDateTime.parse("2026-08-12T01:00:00Z"), null, null));

        mockMvc.perform(get("/api/admin/ai/youtube-channel-watches/{creatorId}", creatorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.subscriptionStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.lastNotificationAt").value("2026-08-12T01:00:00Z"))
                .andExpect(jsonPath("$.lastRenewedAt").isEmpty())
                .andExpect(jsonPath("$.lastErrorCategory").isEmpty());
    }

    @Test
    @DisplayName("ACTIVE 감시 상태를 저장된 필드 그대로 반환한다")
    void 감시조회_ACTIVE상태_저장된필드를그대로반환한다() throws Exception {
        UUID creatorId = UUID.randomUUID();
        when(useCase.getStatus(creatorId)).thenReturn(new YoutubeChannelWatchManagementUseCase.WatchStatus(
                true, "ACTIVE", OffsetDateTime.parse("2026-08-12T01:00:00Z"),
                OffsetDateTime.parse("2026-08-13T02:00:00Z"), null));

        mockMvc.perform(get("/api/admin/ai/youtube-channel-watches/{creatorId}", creatorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.subscriptionStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.lastNotificationAt").value("2026-08-12T01:00:00Z"))
                .andExpect(jsonPath("$.lastRenewedAt").value("2026-08-13T02:00:00Z"))
                .andExpect(jsonPath("$.lastErrorCategory").isEmpty());
    }

    @Test
    @DisplayName("실패 감시 상태를 저장된 필드 그대로 반환한다")
    void 감시조회_실패상태_저장된필드를그대로반환한다() throws Exception {
        UUID creatorId = UUID.randomUUID();
        when(useCase.getStatus(creatorId)).thenReturn(new YoutubeChannelWatchManagementUseCase.WatchStatus(
                true, "RENEWAL_FAILED", OffsetDateTime.parse("2026-08-12T01:00:00Z"),
                OffsetDateTime.parse("2026-08-13T02:00:00Z"), "SUBSCRIPTION_TIMEOUT",
                OffsetDateTime.parse("2026-08-13T03:00:00Z")));

        mockMvc.perform(get("/api/admin/ai/youtube-channel-watches/{creatorId}", creatorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.subscriptionStatus").value("RENEWAL_FAILED"))
                .andExpect(jsonPath("$.lastNotificationAt").value("2026-08-12T01:00:00Z"))
                .andExpect(jsonPath("$.lastRenewedAt").value("2026-08-13T02:00:00Z"))
                .andExpect(jsonPath("$.lastErrorCategory").value("SUBSCRIPTION_TIMEOUT"))
                .andExpect(jsonPath("$.lastErrorAt").value("2026-08-13T03:00:00Z"));
    }

    @Test
    @DisplayName("검증되지 않은 Creator 조회는 404 CREATOR_NOT_FOUND를 반환한다")
    void 감시조회_검증되지않은Creator_404CREATOR_NOT_FOUND를반환한다() throws Exception {
        UUID creatorId = UUID.randomUUID();
        when(useCase.getStatus(creatorId)).thenThrow(
                new BusinessException(HttpStatus.NOT_FOUND, "CREATOR_NOT_FOUND", "요청한 유튜버를 찾을 수 없습니다."));

        mockMvc.perform(get("/api/admin/ai/youtube-channel-watches/{creatorId}", creatorId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"));
    }

    @Test
    @DisplayName("Creator ID 형식이 잘못되면 400 INVALID_FIELD_VALUE를 반환한다")
    void 감시조회_CreatorID형식오류_400INVALID_FIELD_VALUE를반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/ai/youtube-channel-watches/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"));
    }
}
