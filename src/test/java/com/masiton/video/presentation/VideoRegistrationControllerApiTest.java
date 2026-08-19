package com.masiton.video.presentation;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.masiton.video.application.port.in.VideoRegistrationUseCase;
import com.masiton.common.security.LegacyAdminActorResolver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("영상 등록 Controller API")
class VideoRegistrationControllerApiTest {

    private final VideoRegistrationUseCase videoRegistrationUseCase = mock(VideoRegistrationUseCase.class);
    private final LegacyAdminActorResolver legacyAdminActorResolver = mock(LegacyAdminActorResolver.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
            new VideoRegistrationController(videoRegistrationUseCase, legacyAdminActorResolver)).build();
    private final UUID adminId = UUID.randomUUID();

    @Test
    @DisplayName("미리보기 후보에서 내부 식별자를 노출하지 않는다")
    void preview_준비완료_후보의내부식별자를노출하지않는다() throws Exception {
        when(videoRegistrationUseCase.preview(any())).thenReturn(new VideoRegistrationUseCase.VideoPreviewResult(
                VideoRegistrationUseCase.VideoPreviewResult.Decision.READY,
                "opaque-token",
                OffsetDateTime.parse("2026-07-28T00:00:00Z"),
                new VideoRegistrationUseCase.VideoCandidate(
                        UUID.randomUUID(), "영상 제목", "https://image.example/thumbnail.jpg", "채널명", "https://youtu.be/video"),
                null));

        mockMvc.perform(post("/api/admin/video-registration-previews")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceUrl\":\"https://youtu.be/video\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidate.title").value("영상 제목"))
                .andExpect(jsonPath("$.candidate.id").doesNotExist());
    }

    @Test
    @DisplayName("중복 확정은 계약된 오류 코드와 기존 영상 정보를 반환한다")
    void create_중복영상_409과기존정보를반환한다() throws Exception {
        UUID videoId = UUID.randomUUID();
        when(videoRegistrationUseCase.create(any())).thenReturn(new VideoRegistrationUseCase.VideoCreationResult(
                new VideoRegistrationUseCase.VideoCandidate(
                        videoId, "영상 제목", "https://image.example/thumbnail.jpg", "채널명", "https://youtu.be/video"),
                false,
                true));

        mockMvc.perform(post("/api/admin/videos")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationToken\":\"opaque-token\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_VIDEO"))
                .andExpect(jsonPath("$.resource.id").value(videoId.toString()));
    }

    private UsernamePasswordAuthenticationToken authentication() {
        when(legacyAdminActorResolver.resolve(adminId)).thenReturn(adminId);
        return UsernamePasswordAuthenticationToken.authenticated(adminId.toString(), "N/A", java.util.List.of());
    }
}
