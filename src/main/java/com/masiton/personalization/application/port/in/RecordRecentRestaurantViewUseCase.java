package com.masiton.personalization.application.port.in;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface RecordRecentRestaurantViewUseCase {

    void record(UUID memberId, UUID restaurantId, OffsetDateTime viewedAt);
}
