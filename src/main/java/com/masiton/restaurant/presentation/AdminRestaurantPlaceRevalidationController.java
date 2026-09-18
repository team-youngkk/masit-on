package com.masiton.restaurant.presentation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.restaurant.application.port.in.RestaurantPlaceRevalidationUseCase;

@RestController
@RequestMapping("/api/admin/restaurants/{restaurantId}/place-revalidation")
public class AdminRestaurantPlaceRevalidationController {

    private final RestaurantPlaceRevalidationUseCase useCase;

    public AdminRestaurantPlaceRevalidationController(RestaurantPlaceRevalidationUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    public ResponseEntity<RunResponse> run(@PathVariable UUID restaurantId) {
        return useCase.run(restaurantId)
                .map(result -> {
                    RunResponse response = new RunResponse(result.outcome(), result.nextAttemptAt());
                    if ("STALE_DISCARDED".equals(result.outcome())) {
                        throw new BusinessException(org.springframework.http.HttpStatus.CONFLICT,
                                "RESTAURANT_PLACE_REVALIDATION_STALE",
                                "The revalidation result was discarded because the restaurant changed.");
                    }
                    return ResponseEntity.accepted().body(response);
                })
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @GetMapping
    public StateResponse state(@PathVariable UUID restaurantId) {
        var state = useCase.state(restaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return new StateResponse(state.status(), state.attemptCount(), state.nextAttemptAt(),
                state.lastCheckedAt(), state.reasonCode(), state.errorCode());
    }

    @GetMapping("/audits")
    public AuditsResponse audits(@PathVariable UUID restaurantId,
                                 @RequestParam(defaultValue = "20") int size) {
        if (size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "size", "Invalid page size.");
        }
        List<AuditResponse> items = useCase.audits(restaurantId, size).stream()
                .map(audit -> new AuditResponse(audit.outcome().name(), audit.observedValues(),
                        audit.previousValues(), audit.appliedValues(), audit.reasonCode(), audit.errorCode(),
                        audit.checkedAt(), audit.nextAttemptAt()))
                .toList();
        return new AuditsResponse(items);
    }

    public record RunResponse(String outcome, OffsetDateTime nextAttemptAt) {
    }

    public record StateResponse(String status, int attemptCount, OffsetDateTime nextAttemptAt,
                                OffsetDateTime lastCheckedAt, String reasonCode, String errorCode) {
    }

    public record AuditResponse(String status, Map<String, Object> observedValues,
                                Map<String, Object> previousValues, Map<String, Object> appliedValues,
                                String reasonCode, String errorCode, OffsetDateTime checkedAt,
                                OffsetDateTime nextAttemptAt) {
    }

    public record AuditsResponse(List<AuditResponse> items) {
    }
}
