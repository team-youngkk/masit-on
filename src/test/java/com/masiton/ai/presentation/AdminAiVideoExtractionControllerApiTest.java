package com.masiton.ai.presentation;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.masiton.ai.application.port.in.AiExtractionJobUseCase;
import com.masiton.ai.application.port.out.dto.AiExtractionJobView;

@DisplayName("관리자 AI 영상 추출 Controller API")
class AdminAiVideoExtractionControllerApiTest {

    private final AiExtractionJobUseCase useCase = mock(AiExtractionJobUseCase.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
            new AdminAiVideoExtractionController(useCase)).build();

    @Test
    @DisplayName("신규 접수는 202와 공통 필드 null 키를 포함한다")
    void submit_신규접수_202와공통필드null키를포함한다() throws Exception {
        when(useCase.submitAdmin(any(), any(), any())).thenReturn(new AiExtractionJobView(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                "ADMIN",
                "channel-id",
                "video-id",
                "https://www.youtube.com/watch?v=video-id",
                "QUEUED",
                null,
                null,
                "GOOGLE_GEMINI",
                "gemini-3-flash-preview",
                "P1",
                "S1",
                0,
                OffsetDateTime.parse("2026-08-11T00:00:00Z"),
                null,
                null,
                false
        ));

        mockMvc.perform(post("/api/admin/ai/video-extractions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "videoUrl": "https://youtu.be/video-id"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.executionStatus").value("QUEUED"))
                .andExpect(jsonPath("$.resultCompleteness").hasJsonPath())
                .andExpect(jsonPath("$.resultCompleteness").value(nullValue()))
                .andExpect(jsonPath("$.reviewStatus").hasJsonPath())
                .andExpect(jsonPath("$.reviewStatus").value(nullValue()))
                .andExpect(jsonPath("$.startedAt").hasJsonPath())
                .andExpect(jsonPath("$.startedAt").value(nullValue()))
                .andExpect(jsonPath("$.finishedAt").hasJsonPath())
                .andExpect(jsonPath("$.finishedAt").value(nullValue()))
                .andExpect(jsonPath("$.youtube.channelId").value("channel-id"))
                .andExpect(jsonPath("$.youtube.videoId").value("video-id"))
                .andExpect(jsonPath("$.reused").value(false));
    }

    @Test
    @DisplayName("기존 작업 재사용은 200과 공통 필드 값을 포함한다")
    void submit_기존작업재사용_200과공통필드값을포함한다() throws Exception {
        when(useCase.submitAdmin(any(), any(), any())).thenReturn(new AiExtractionJobView(
                UUID.fromString("22222222-2222-4222-8222-222222222222"),
                "WEBHOOK",
                "channel-id",
                "video-id",
                "https://www.youtube.com/watch?v=video-id",
                "SUCCEEDED",
                "PARTIAL",
                "AUTO_BLOCKED",
                "GOOGLE_GEMINI",
                "gemini-3-flash-preview",
                "P1",
                "S1",
                2,
                OffsetDateTime.parse("2026-08-11T00:00:00Z"),
                OffsetDateTime.parse("2026-08-11T00:01:00Z"),
                OffsetDateTime.parse("2026-08-11T00:03:00Z"),
                true
        ));

        mockMvc.perform(post("/api/admin/ai/video-extractions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "videoUrl": "https://www.youtube.com/watch?v=video-id",
                                  "supplementText": "확인 메모"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.resultCompleteness").value("PARTIAL"))
                .andExpect(jsonPath("$.reviewStatus").value("AUTO_BLOCKED"))
                .andExpect(jsonPath("$.startedAt").value("2026-08-11T00:01:00Z"))
                .andExpect(jsonPath("$.finishedAt").value("2026-08-11T00:03:00Z"))
                .andExpect(jsonPath("$.reused").value(true));
    }
}
