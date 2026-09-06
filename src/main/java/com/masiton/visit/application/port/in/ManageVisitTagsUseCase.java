package com.masiton.visit.application.port.in;

import java.util.List;

public interface ManageVisitTagsUseCase {
    Result list(String restaurantId);
    void replace(String restaurantId, String visitId, Change change, String memberId);

    record Change(String expectedVersion, List<String> tagCodes, String reason) { }
    record Tag(String code, String displayName, String type, String source) { }
    record TagOption(String code, String displayName, String type) { }
    record Item(String visitId, String creatorName, String videoTitle, String videoUrl,
                String version, List<Tag> tags) { }
    record Result(List<Item> items, List<TagOption> tagOptions) { }
}
