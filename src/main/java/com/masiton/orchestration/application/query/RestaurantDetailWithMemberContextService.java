package com.masiton.orchestration.application.query;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.masiton.orchestration.application.port.in.GetRestaurantDetailQuery;
import com.masiton.orchestration.application.port.in.GetRestaurantDetailWithMemberContextQuery;
import com.masiton.personal.application.port.in.RecordRecentRestaurantViewUseCase;

@Service
public class RestaurantDetailWithMemberContextService implements GetRestaurantDetailWithMemberContextQuery {

    private static final Logger log = LoggerFactory.getLogger(RestaurantDetailWithMemberContextService.class);

    private final GetRestaurantDetailQuery getRestaurantDetailQuery;
    private final RecordRecentRestaurantViewUseCase recordRecentRestaurantViewUseCase;

    public RestaurantDetailWithMemberContextService(
            GetRestaurantDetailQuery getRestaurantDetailQuery,
            RecordRecentRestaurantViewUseCase recordRecentRestaurantViewUseCase
    ) {
        this.getRestaurantDetailQuery = getRestaurantDetailQuery;
        this.recordRecentRestaurantViewUseCase = recordRecentRestaurantViewUseCase;
    }

    @Override
    public RestaurantDetailResult getRestaurantDetail(UUID restaurantId, Optional<UUID> memberId) {
        RestaurantDetailResult result = getRestaurantDetailQuery.getRestaurantDetail(restaurantId);
        memberId.ifPresent(id -> recordRecentView(id, restaurantId));
        return result;
    }

    private void recordRecentView(UUID memberId, UUID restaurantId) {
        try {
            recordRecentRestaurantViewUseCase.record(memberId, restaurantId);
        } catch (RuntimeException exception) {
            log.warn("최근 본 맛집 기록 실패: cause={}", exception.getClass().getSimpleName());
        }
    }
}
