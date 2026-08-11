package com.masiton.ai.presentation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.ai.application.port.in.AiExtractionJobUseCase;
import com.masiton.ai.application.port.out.dto.AiExtractionJobView;

@RestController
@RequestMapping("/api/admin/ai/video-extractions")
public class AdminAiVideoExtractionController {

    private final AiExtractionJobUseCase useCase;

    public AdminAiVideoExtractionController(AiExtractionJobUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    public ResponseEntity<AiExtractionJobResponse> submit(@Valid @RequestBody SubmitRequest request) {
        AiExtractionJobView view = useCase.submitAdmin(request.videoUrl(), request.supplementText(), request.idempotencyKey());
        return ResponseEntity.status(view.reused() ? 200 : 202).body(AiExtractionJobResponse.from(view));
    }

    public record SubmitRequest(
            @jakarta.validation.constraints.NotBlank String videoUrl,
            @Size(max = 20_000) String supplementText,
            @Size(max = 200) String idempotencyKey
    ) {
    }

    public record AiExtractionJobResponse(
            java.util.UUID jobId,
            String source,
            YoutubeReference youtube,
            String executionStatus,
            String resultCompleteness,
            String reviewStatus,
            String provider,
            String modelVersion,
            String promptVersion,
            String schemaVersion,
            int attemptCount,
            java.time.OffsetDateTime createdAt,
            java.time.OffsetDateTime startedAt,
            java.time.OffsetDateTime finishedAt,
            boolean reused
    ) {
        static AiExtractionJobResponse from(AiExtractionJobView view) {
            return new AiExtractionJobResponse(view.jobId(), view.source(),
                    new YoutubeReference(view.channelId(), view.videoId(), view.videoUrl()),
                    view.executionStatus(), view.resultCompleteness(), view.reviewStatus(), view.provider(),
                    view.modelVersion(), view.promptVersion(), view.schemaVersion(), view.attemptCount(),
                    view.createdAt(), view.startedAt(), view.finishedAt(), view.reused());
        }
    }

    public record YoutubeReference(String channelId, String videoId, String videoUrl) {
    }
}
