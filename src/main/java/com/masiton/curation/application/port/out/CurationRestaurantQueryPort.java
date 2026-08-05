package com.masiton.curation.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CurationRestaurantQueryPort {
    List<RestaurantProjection> findAll(Collection<UUID> restaurantIds);

    record RestaurantProjection(UUID id, String name, String publicationStatus, String lifecycleStatus) {
        public boolean publiclyVisible() {
            return "PUBLIC".equals(publicationStatus) && "ACTIVE".equals(lifecycleStatus);
        }
        public String availability() {
            if (!"ACTIVE".equals(lifecycleStatus)) return "INACTIVE";
            return publicationStatus;
        }
    }
}
