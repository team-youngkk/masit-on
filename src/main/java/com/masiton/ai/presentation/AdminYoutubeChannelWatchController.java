package com.masiton.ai.presentation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.ai.application.port.in.YoutubeChannelWatchManagementUseCase;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;

@RestController
@RequestMapping("/api/admin/ai/youtube-channel-watches")
public class AdminYoutubeChannelWatchController {

    private static final Set<Integer> ALLOWED_SIZES = Set.of(10, 20, 50);

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

    @GetMapping
    public WatchPageResponse list(@RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        if (page < 1) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "page", "Invalid page request.");
        }
        if (!ALLOWED_SIZES.contains(size)) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "size", "Invalid page size.");
        }
        YoutubeChannelWatchManagementUseCase.WatchPage result = useCase.getStatuses(page, size);
        return new WatchPageResponse(result.items().stream().map(WatchSummaryResponse::from).toList(),
                new PageMetadata(result.number(), result.size(), result.totalElements(), result.totalPages(), result.hasNext()));
    }

    public record WatchRequest(Boolean enabled) { }

    public record WatchResponse(boolean enabled, String subscriptionStatus, OffsetDateTime lastNotificationAt,
                                OffsetDateTime lastRenewedAt, String lastErrorCategory, OffsetDateTime lastErrorAt) {
        static WatchResponse from(YoutubeChannelWatchManagementUseCase.WatchStatus status) {
            return new WatchResponse(status.enabled(), status.subscriptionStatus(), status.lastNotificationAt(),
                    status.lastRenewedAt(), status.lastErrorCategory(), status.lastErrorAt());
        }
    }

    public record WatchSummaryResponse(UUID creatorId, String channelName, boolean publiclyVisible,
                                       boolean externallyAvailable, WatchResponse status) {
        static WatchSummaryResponse from(YoutubeChannelWatchManagementUseCase.WatchSummary summary) {
            return new WatchSummaryResponse(summary.creatorId(), summary.channelName(), summary.publiclyVisible(),
                    summary.externallyAvailable(), WatchResponse.from(summary.status()));
        }
    }

    public record WatchPageResponse(List<WatchSummaryResponse> items, PageMetadata page) { }

    public record PageMetadata(int number, int size, long totalElements, long totalPages, boolean hasNext) { }
}
