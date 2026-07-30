package com.masiton.personal.application.port.in;

import java.util.List;

public record PersonalRestaurantPage(
        List<PersonalRestaurantItem> items,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
