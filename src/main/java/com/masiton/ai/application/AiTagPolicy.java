package com.masiton.ai.application;

import java.util.Locale;

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
        String candidate = normalize(label);
        if (candidate.equals(normalize(definition.displayName()))) {
            return true;
        }
        try {
            JsonNode aliases = objectMapper.readTree(definition.aliases() == null ? "[]" : definition.aliases());
            if (!aliases.isArray()) {
                return false;
            }
            for (JsonNode alias : aliases) {
                if (alias.isTextual() && candidate.equals(normalize(alias.textValue()))) {
                    return true;
                }
            }
        } catch (JacksonException exception) {
            return false;
        }
        return false;
    }

    static boolean isNewTagCandidate(String tagType, String rawLabel, String label, String normalizedCode) {
        String normalizedLabel = normalize(label);
        String normalizedRawLabel = normalize(rawLabel);
        return !normalizedLabel.isBlank() && normalizedLabel.equals(normalizedRawLabel)
                && normalizedCode != null
                && normalizedCode.matches(java.util.regex.Pattern.quote(tagType) + "_[A-Z0-9][A-Z0-9_]{0,63}");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
