package com.masiton.personal.presentation;

import java.time.OffsetDateTime;
import java.util.List;

import com.masiton.personal.application.port.in.PersonalRestaurantItem;
import com.masiton.personal.application.port.in.PersonalRestaurantPage;

final class PersonalRestaurantResponse {

    private PersonalRestaurantResponse() {
    }

    record FavoriteState(String restaurantId, boolean favorited) {
    }

    record RecentState(String restaurantId, boolean recorded) {
    }

    record FavoriteList(List<FavoriteItem> items, Page page) {

        static FavoriteList from(PersonalRestaurantPage result) {
            return new FavoriteList(
                    result.items().stream().map(FavoriteItem::from).toList(),
                    new Page(result.number(), result.size(), result.totalElements(),
                            result.totalPages(), result.hasNext()));
        }
    }

    record RecentList(List<RecentItem> items, Page page) {

        static RecentList from(PersonalRestaurantPage result) {
            return new RecentList(
                    result.items().stream().map(RecentItem::from).toList(),
                    new Page(result.number(), result.size(), result.totalElements(),
                            result.totalPages(), result.hasNext()));
        }
    }

    record FavoriteItem(Restaurant restaurant, OffsetDateTime favoritedAt) {

        static FavoriteItem from(PersonalRestaurantItem item) {
            return new FavoriteItem(PersonalRestaurantResponse.restaurant(item), item.occurredAt());
        }
    }

    record RecentItem(Restaurant restaurant, OffsetDateTime lastViewedAt) {

        static RecentItem from(PersonalRestaurantItem item) {
            return new RecentItem(PersonalRestaurantResponse.restaurant(item), item.occurredAt());
        }
    }

    private static Restaurant restaurant(PersonalRestaurantItem item) {
        return new Restaurant(item.restaurantId().toString(), item.name(),
                item.district(), item.category());
    }

    record Restaurant(String id, String name, String district, String category) {
    }

    record Page(int number, int size, long totalElements, int totalPages, boolean hasNext) {
    }
}
