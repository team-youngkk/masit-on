package com.masiton.ai.application.port.in;

import java.util.List;

public interface ManageTagDefinitionsUseCase {
    Result listActive();

    TagDefinition create(CreateCommand command);

    record CreateCommand(String code, String type, String displayName, List<String> aliases) {
    }

    record TagDefinition(String code, String type, String displayName, List<String> aliases,
                         String status, String source) {
    }

    record Result(List<TagDefinition> items) {
    }
}
