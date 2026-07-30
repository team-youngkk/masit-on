package com.masiton.orchestration.application.query;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.masiton.orchestration.application.port.in.GetRestaurantDetailQuery;
import com.masiton.orchestration.application.port.in.GetRestaurantDetailWithMemberContextQuery;
import com.masiton.personalization.application.port.in.RecordRecentRestaurantViewUseCase;

@Service
public class RestaurantDetailWithMemberContextService implements GetRestaurantDetailWithMemberContextQuery {

    private static final Logger log = LoggerFactory.getLogger(RestaurantDetailWithMemberContextService.class);

    private final GetRestaurantDetailQuery getRestaurantDetailQuery;
    private final RecordRecentRestaurantViewUseCase recordRecentRestaurantViewUseCase;
    private final Clock clock;

    public RestaurantDetailWithMemberContextService(
            GetRestaurantDetailQuery getRestaurantDetailQuery,
            RecordRecentRestaurantViewUseCase recordRecentRestaurantViewUseCase,
            @Qualifier("personalizationClock") Clock clock
    ) {
        this.getRestaurantDetailQuery = getRestaurantDetailQuery;
        this.recordRecentRestaurantViewUseCase = recordRecentRestaurantViewUseCase;
        this.clock = clock;
    }

    @Override
    public RestaurantDetailResult getRestaurantDetail(UUID restaurantId, Optional<UUID> memberId) {
        RestaurantDetailResult result = getRestaurantDetailQuery.getRestaurantDetail(restaurantId);
        memberId.ifPresent(id -> recordRecentView(id, restaurantId));
        return result;
    }

    private void recordRecentView(UUID memberId, UUID restaurantId) {
        try {
            recordRecentRestaurantViewUseCase.record(
                    memberId, restaurantId, OffsetDateTime.now(clock));
        } catch (RuntimeException exception) {
            log.warn("최근 본 맛집 기록 실패: cause={}", exception.getClass().getSimpleName());
        }
    }
}
