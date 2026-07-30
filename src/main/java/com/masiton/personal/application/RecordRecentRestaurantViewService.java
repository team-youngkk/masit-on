package com.masiton.personal.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.personal.application.port.in.RecordRecentRestaurantViewUseCase;
import com.masiton.personal.application.port.out.RecentRestaurantViewRepository;

@Service
public class RecordRecentRestaurantViewService implements RecordRecentRestaurantViewUseCase {
    private final RecentRestaurantViewRepository repository;
    private final Clock clock;

    public RecordRecentRestaurantViewService(RecentRestaurantViewRepository repository, Clock memberSessionClock) {
        this.repository = repository;
        this.clock = memberSessionClock;
    }

    @Override
    @Transactional
    public void record(UUID memberId, UUID restaurantId) {
        repository.upsert(memberId, restaurantId, Instant.now(clock));
    }
}
