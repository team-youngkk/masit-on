package com.masiton.ai.presentation;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.ai.application.port.in.YoutubeChannelWatchManagementUseCase;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;

@RestController
@RequestMapping("/api/admin/ai/youtube-channel-watches")
public class AdminYoutubeChannelWatchController {

    private final YoutubeChannelWatchManagementUseCase useCase;

    public AdminYoutubeChannelWatchController(YoutubeChannelWatchManagementUseCase useCase) {
        this.useCase = useCase;
    }

    @PutMapping("/{creatorId}")
    public ResponseEntity<WatchResponse> setEnabled(@PathVariable UUID creatorId, @RequestBody WatchRequest request) {
        if (request == null || request.enabled() == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD, "enabled", "enabled is required.");
        }
        return ResponseEntity.ok(WatchResponse.from(useCase.setEnabled(creatorId, request.enabled())));
    }

    @GetMapping("/{creatorId}")
    public ResponseEntity<WatchResponse> getStatus(@PathVariable UUID creatorId) {
        return ResponseEntity.ok(WatchResponse.from(useCase.getStatus(creatorId)));
    }

    public record WatchRequest(Boolean enabled) { }

    public record WatchResponse(boolean enabled, String subscriptionStatus, OffsetDateTime lastNotificationAt,
                                OffsetDateTime lastRenewedAt, String lastErrorCategory, OffsetDateTime lastErrorAt) {
        static WatchResponse from(YoutubeChannelWatchManagementUseCase.WatchStatus status) {
            return new WatchResponse(status.enabled(), status.subscriptionStatus(), status.lastNotificationAt(),
                    status.lastRenewedAt(), status.lastErrorCategory(), status.lastErrorAt());
        }
    }
}
