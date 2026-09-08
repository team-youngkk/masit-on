package com.masiton.ai.application.port.out;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase.TagDefinition;
import com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase.AuditEntry;

public interface TagDefinitionStore {
    List<TagDefinition> findActive();

    TagDefinition create(NewTagDefinition definition);

    List<TagDefinition> find(String status, int offset, int size);
    long count(String status);
    TagDefinition get(String code);
    TagDefinition update(Change definition);
    List<AuditEntry> history(String code, int offset, int size);
    long historyCount(String code);

    record NewTagDefinition(UUID id, String code, String type, String displayName, List<String> aliases,
                            List<String> normalizedTerms, OffsetDateTime createdAt) {
    }

    record Change(String code, long expectedVersion, String displayName, List<String> aliases,
                  List<String> normalizedTerms, String status, String action, String reason,
                  UUID memberId, OffsetDateTime changedAt) { }

    class DuplicateCodeException extends RuntimeException {
    }

    class DuplicateTermException extends RuntimeException {
        public DuplicateTermException(Throwable cause) {
            super(cause);
        }
    }


    class NotFoundException extends RuntimeException { }

    class ConcurrentUpdateException extends RuntimeException { }
}
