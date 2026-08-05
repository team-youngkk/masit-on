package com.masiton.personal.application.port.in;

import java.util.UUID;

public record CollectionOption(UUID collectionId, String name, long restaurantCount,
                               AdditionStatus additionStatus) {

    public enum AdditionStatus {
        AVAILABLE,
        ALREADY_INCLUDED,
        LIMIT_REACHED
    }
}
