package com.masiton.ai.application.port.out;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase.TagDefinition;

public interface TagDefinitionStore {
    List<TagDefinition> findActive();

    TagDefinition create(NewTagDefinition definition);

    record NewTagDefinition(UUID id, String code, String type, String displayName, List<String> aliases,
                            List<String> normalizedTerms, OffsetDateTime createdAt) {
    }

    class DuplicateCodeException extends RuntimeException {
    }

    class DuplicateTermException extends RuntimeException {
        public DuplicateTermException(Throwable cause) {
            super(cause);
        }
    }
}
