package com.masiton.restaurant.application.port.out;

import java.util.UUID;

public record VisitedByRow(UUID restaurantId, UUID creatorId, String channelName) {
}
