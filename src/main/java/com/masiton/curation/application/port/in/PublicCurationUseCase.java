package com.masiton.curation.application.port.in;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface PublicCurationUseCase {

    List<PublicCuration> getPublishedCurations();

    PublicCuration getPublishedCuration(UUID curationId);

    record PublicCuration(
            UUID curationId,
            String title,
            String description,
            List<RestaurantItem> items,
            OffsetDateTime publishedAt,
            OffsetDateTime updatedAt
    ) { }

    record RestaurantItem(UUID restaurantId, String name, String roadAddress) { }
}
