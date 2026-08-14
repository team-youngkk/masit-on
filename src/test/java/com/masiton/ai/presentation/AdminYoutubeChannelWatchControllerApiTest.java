package com.masiton.ai.presentation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.masiton.ai.application.port.in.YoutubeChannelWatchManagementUseCase;
import com.masiton.common.web.GlobalExceptionHandler;

@DisplayName("관리자 YouTube 채널 감시 Controller API")
class AdminYoutubeChannelWatchControllerApiTest {

    private final YoutubeChannelWatchManagementUseCase useCase = mock(YoutubeChannelWatchManagementUseCase.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AdminYoutubeChannelWatchController(useCase))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

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
}
