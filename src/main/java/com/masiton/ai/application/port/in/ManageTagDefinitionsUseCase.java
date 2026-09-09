package com.masiton.ai.application.port.in;

import java.time.OffsetDateTime;
import java.util.List;

public interface ManageTagDefinitionsUseCase {
    Result listActive();

    TagDefinition create(CreateCommand command);

    ManagementResult list(String status, int page, int size);

    TagDefinition get(String code);

    TagDefinition update(String code, UpdateCommand command, String memberId);

    TagDefinition changeStatus(String code, StatusCommand command, String memberId);

    HistoryResult history(String code, int page, int size);

    record CreateCommand(String code, String type, String displayName, List<String> aliases) {
    }

    record UpdateCommand(Long expectedVersion, String displayName, List<String> aliases, String reason) { }

    record StatusCommand(Long expectedVersion, String status, String reason) { }

    record TagDefinition(String code, String type, String displayName, List<String> aliases,
                         String status, String source, long version) {
    }

    record Result(List<TagDefinition> items) {
    }

    record ManagementResult(List<TagDefinition> items, Page page) { }

    record HistoryResult(List<AuditEntry> items, Page page) { }

    record Page(int number, int size, long totalElements, int totalPages, boolean hasNext) { }

    record AuditEntry(String id, String action, TagDefinition before, TagDefinition after, String reason,
                      String changedByMemberId, OffsetDateTime changedAt, long version) { }
}
