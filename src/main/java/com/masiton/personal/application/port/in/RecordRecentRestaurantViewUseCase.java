package com.masiton.personal.application.port.in;

import java.util.UUID;

public interface RecordRecentRestaurantViewUseCase {
    void record(UUID memberId, UUID restaurantId);
}
