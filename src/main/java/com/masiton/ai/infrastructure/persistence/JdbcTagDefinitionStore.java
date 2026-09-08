package com.masiton.ai.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase.TagDefinition;
import com.masiton.ai.application.port.out.TagDefinitionStore;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
class JdbcTagDefinitionStore implements TagDefinitionStore {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    JdbcTagDefinitionStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<TagDefinition> findActive() {
        return jdbcTemplate.query("""
                SELECT tag_code, tag_type, display_name, aliases::text, status, source
                  FROM tag_definition
                 WHERE status = 'ACTIVE'
                 ORDER BY tag_type, normalize_tag_definition_term(display_name), tag_code
                """, this::map);
    }

    @Override
    public TagDefinition create(NewTagDefinition definition) {
        definition.normalizedTerms().stream().sorted().forEach(term -> jdbcTemplate.queryForObject(
                "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(?, 0))) locked",
                Integer.class, term));
        String aliasesJson = writeAliases(definition.aliases());
        int inserted = jdbcTemplate.update("""
                INSERT INTO tag_definition (
                    id, tag_code, tag_type, display_name, aliases, status, source,
                    created_from_snapshot_id, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?::jsonb, 'ACTIVE', 'MANUAL_OVERRIDE', NULL, ?, ?)
                ON CONFLICT (tag_code) DO NOTHING
                """, definition.id(), definition.code(), definition.type(), definition.displayName(), aliasesJson,
                definition.createdAt(), definition.createdAt());
        if (inserted == 0) {
            throw new DuplicateCodeException();
        }
        try {
            for (int index = 0; index < definition.normalizedTerms().size(); index++) {
                jdbcTemplate.update("""
                        INSERT INTO tag_definition_term (
                            id, tag_definition_id, term_kind, normalized_term, created_at
                        ) VALUES (?, ?, ?, ?, ?)
                        """, UUID.randomUUID(), definition.id(), index == 0 ? "DISPLAY_NAME" : "ALIAS",
                        definition.normalizedTerms().get(index), definition.createdAt());
            }
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateTermException(exception);
        }
        return new TagDefinition(definition.code(), definition.type(), definition.displayName(),
                definition.aliases(), "ACTIVE", "MANUAL_OVERRIDE");
    }

    private TagDefinition map(ResultSet resultSet, int rowNum) throws SQLException {
        try {
            List<String> aliases = objectMapper.readValue(resultSet.getString("aliases"), STRING_LIST);
            return new TagDefinition(resultSet.getString("tag_code"), resultSet.getString("tag_type"),
                    resultSet.getString("display_name"), aliases, resultSet.getString("status"),
                    resultSet.getString("source"));
        } catch (JacksonException exception) {
            throw new SQLException("Invalid tag aliases JSON.", exception);
        }
    }

    private String writeAliases(List<String> aliases) {
        try {
            return objectMapper.writeValueAsString(aliases);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize tag aliases.", exception);
        }
    }
}
