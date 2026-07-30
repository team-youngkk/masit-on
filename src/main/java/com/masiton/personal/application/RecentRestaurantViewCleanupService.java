package com.masiton.personal.application;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.personal.application.port.in.CleanupRecentRestaurantViewsUseCase;
import com.masiton.personal.application.port.out.PersonalRestaurantStore;

@Service
public class RecentRestaurantViewCleanupService implements CleanupRecentRestaurantViewsUseCase {

    private static final int RETENTION_DAYS = 30;

    private final PersonalRestaurantStore store;
    private final Clock clock;

    public RecentRestaurantViewCleanupService(
            PersonalRestaurantStore store,
            @Qualifier("personalizationClock") Clock clock
    ) {
        this.store = store;
        this.clock = clock;
    }

    @Override
    @Transactional
    public int cleanupExpiredViews() {
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minusDays(RETENTION_DAYS);
        return store.deleteRecentRestaurantViewsBefore(cutoff);
    }
}
