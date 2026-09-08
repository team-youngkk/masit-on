package com.masiton.ai.application;

import com.masiton.ai.application.port.out.AiExtractionResultStore;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Deterministic policy shared by pre-commit resolution and the atomic commit transaction. */
final class AiTagPolicy {

    private AiTagPolicy() {
    }

    static boolean matchesApprovedLabel(String label, AiExtractionResultStore.TagDefinition definition,
                                        ObjectMapper objectMapper) {
        String candidate = TagTermNormalizer.normalize(label);
        if (candidate.equals(TagTermNormalizer.normalize(definition.displayName()))) {
            return true;
        }
        try {
            JsonNode aliases = objectMapper.readTree(definition.aliases() == null ? "[]" : definition.aliases());
            if (!aliases.isArray()) {
                return false;
            }
            for (JsonNode alias : aliases) {
                if (alias.isTextual() && candidate.equals(TagTermNormalizer.normalize(alias.textValue()))) {
                    return true;
                }
            }
        } catch (JacksonException exception) {
            return false;
        }
        return false;
    }

    static boolean isNewTagCandidate(String tagType, String rawLabel, String label, String normalizedCode) {
        String normalizedLabel = TagTermNormalizer.normalize(label);
        String normalizedRawLabel = TagTermNormalizer.normalize(rawLabel);
        return hasValidRawLength(rawLabel) && hasValidRawLength(label)
                && !normalizedLabel.isBlank() && normalizedLabel.equals(normalizedRawLabel)
                && normalizedLabel.length() <= TagTermNormalizer.MAX_NORMALIZED_LENGTH
                && normalizedCode != null
                && normalizedCode.length() <= 64
                && normalizedCode.matches(java.util.regex.Pattern.quote(tagType)
                        + "_[A-Z0-9]+(?:_[A-Z0-9]+)*");
    }

    private static boolean hasValidRawLength(String value) {
        return value != null && value.length() <= TagTermNormalizer.MAX_RAW_LENGTH;
    }

}
