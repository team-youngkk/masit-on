package com.masiton.restaurant.presentation.naturallanguage;

import java.util.List;

import com.masiton.restaurant.application.port.in.NaturalLanguageInterpretationView;
import com.masiton.restaurant.application.port.in.NaturalLanguageSearchResult;
import com.masiton.restaurant.application.port.in.RestaurantSearchResult;
import com.masiton.restaurant.application.port.in.RestaurantSummary;
import com.masiton.restaurant.application.port.in.VisitedCreatorSummary;

public record NaturalLanguageSearchResponse(
        Interpretation interpretation,
        Results results
) {

    static NaturalLanguageSearchResponse from(NaturalLanguageSearchResult result) {
        return new NaturalLanguageSearchResponse(
                Interpretation.from(result.interpretation()),
                Results.from(result.results()));
    }

    public record Interpretation(
            NaturalLanguageInterpretationView.Status status,
            AppliedConditions appliedConditions,
            List<IgnoredCondition> ignoredConditions,
            List<Conflict> conflicts,
            String parserVersion
    ) {

        static Interpretation from(NaturalLanguageInterpretationView view) {
            return new Interpretation(
                    view.status(),
                    new AppliedConditions(
                            view.appliedConditions().query(),
                            view.appliedConditions().district(),
                            view.appliedConditions().category(),
                            view.appliedConditions().creatorId(),
                            view.appliedConditions().tags()),
                    view.ignoredConditions().stream()
                            .map(value -> new IgnoredCondition(value.type(), value.text(), value.reason()))
                            .toList(),
                    view.conflicts().stream()
                            .map(value -> new Conflict(value.field(), value.resolution()))
                            .toList(),
                    view.parserVersion());
        }
    }

    public record AppliedConditions(
            String query,
            String district,
            String category,
            String creatorId,
            List<String> tags
    ) {
    }

    public record IgnoredCondition(String type, String text, String reason) {
    }

    public record Conflict(String field, String resolution) {
    }

    public record Results(List<Item> items, PageInfo page) {

        static Results from(RestaurantSearchResult result) {
            return new Results(
                    result.items().stream().map(Item::from).toList(),
                    new PageInfo(
                            result.pageNumber(),
                            result.pageSize(),
                            result.totalElements(),
                            result.totalPages(),
                            result.hasNext()));
        }
    }

    public record Item(
            String id,
            String name,
            String district,
            String category,
            List<VisitedBy> visitedBy,
            int remainingVisitedByCount
    ) {

        static Item from(RestaurantSummary summary) {
            return new Item(
                    summary.id().toString(),
                    summary.name(),
                    summary.district(),
                    summary.category(),
                    summary.visitedBy().stream().map(VisitedBy::from).toList(),
                    summary.remainingVisitedByCount());
        }
    }

    public record VisitedBy(String id, String channelName) {

        static VisitedBy from(VisitedCreatorSummary summary) {
            return new VisitedBy(summary.id().toString(), summary.channelName());
        }
    }

    public record PageInfo(int number, int size, long totalElements, int totalPages, boolean hasNext) {
    }
}
