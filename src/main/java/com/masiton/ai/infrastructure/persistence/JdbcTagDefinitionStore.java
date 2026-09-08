package com.masiton.ai.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase.AuditEntry;
import com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase.TagDefinition;
import com.masiton.ai.application.port.out.TagDefinitionStore;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
class JdbcTagDefinitionStore implements TagDefinitionStore {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private static final String COLUMNS = "tag_code, tag_type, display_name, aliases::text, status, source, version";
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    JdbcTagDefinitionStore(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    @Override public List<TagDefinition> findActive() {
        return jdbc.query("SELECT " + COLUMNS + " FROM tag_definition WHERE status = 'ACTIVE' ORDER BY tag_type, normalize_tag_definition_term(display_name), tag_code", this::map);
    }

    @Override public TagDefinition create(NewTagDefinition definition) {
        lockTerms(definition.normalizedTerms());
        int inserted = jdbc.update("""
                INSERT INTO tag_definition (id, tag_code, tag_type, display_name, aliases, status, source,
                    created_from_snapshot_id, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?::jsonb, 'ACTIVE', 'MANUAL_OVERRIDE', NULL, ?, ?, 0)
                ON CONFLICT (tag_code) DO NOTHING
                """, definition.id(), definition.code(), definition.type(), definition.displayName(), json(definition.aliases()), definition.createdAt(), definition.createdAt());
        if (inserted == 0) throw new DuplicateCodeException();
        insertTerms(definition.id(), definition.normalizedTerms(), definition.createdAt());
        return new TagDefinition(definition.code(), definition.type(), definition.displayName(), definition.aliases(), "ACTIVE", "MANUAL_OVERRIDE", 0);
    }

    @Override public List<TagDefinition> find(String status, long offset, int size) {
        if (status.equals("ALL")) return jdbc.query("SELECT " + COLUMNS + " FROM tag_definition ORDER BY tag_code LIMIT ? OFFSET ?", this::map, size, offset);
        return jdbc.query("SELECT " + COLUMNS + " FROM tag_definition WHERE status = ? ORDER BY tag_code LIMIT ? OFFSET ?", this::map, status, size, offset);
    }

    @Override public long count(String status) {
        if (status.equals("ALL")) return jdbc.queryForObject("SELECT count(*) FROM tag_definition", Long.class);
        return jdbc.queryForObject("SELECT count(*) FROM tag_definition WHERE status = ?", Long.class, status);
    }

    @Override public TagDefinition get(String code) {
        List<TagDefinition> values = jdbc.query("SELECT " + COLUMNS + " FROM tag_definition WHERE tag_code = ?", this::map, code);
        if (values.isEmpty()) throw new NotFoundException();
        return values.getFirst();
    }

    @Override public TagDefinition update(Change change) {
        List<LockedTag> values = jdbc.query("SELECT id, " + COLUMNS + " FROM tag_definition WHERE tag_code = ? FOR UPDATE", this::mapLocked, change.code());
        if (values.isEmpty()) throw new NotFoundException();
        LockedTag locked = values.getFirst();
        TagDefinition before = locked.definition();
        if (before.version() != change.expectedVersion()) throw new ConcurrentUpdateException();
        String displayName = change.displayName() == null ? before.displayName() : change.displayName();
        List<String> aliases = change.aliases() == null ? before.aliases() : change.aliases();
        String status = change.status() == null ? before.status() : change.status();
        if (displayName.equals(before.displayName()) && aliases.equals(before.aliases()) && status.equals(before.status())) return before;
        long nextVersion = before.version() + 1;
        if (change.displayName() != null) {
            List<String> currentTerms = jdbc.queryForList(
                    "SELECT normalized_term FROM tag_definition_term WHERE tag_definition_id = ?",
                    String.class, locked.id());
            lockTerms(Stream.concat(currentTerms.stream(), change.normalizedTerms().stream()).distinct().toList());
            jdbc.update("DELETE FROM tag_definition_term WHERE tag_definition_id = ?", locked.id());
            insertTerms(locked.id(), change.normalizedTerms(), change.changedAt());
        }
        try {
            jdbc.update("UPDATE tag_definition SET display_name = ?, aliases = ?::jsonb, status = ?, version = ?, updated_at = ? WHERE id = ?",
                    displayName, json(aliases), status, nextVersion, change.changedAt(), locked.id());
            TagDefinition after = new TagDefinition(before.code(), before.type(), displayName, aliases, status, before.source(), nextVersion);
            jdbc.update("""
                    INSERT INTO tag_definition_audit(id, tag_definition_id, action, before_snapshot, after_snapshot,
                        reason, changed_by_member_id, changed_at, version)
                    VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?)
                    """, UUID.randomUUID(), locked.id(), change.action(), json(before), json(after), change.reason(), change.memberId(), change.changedAt(), nextVersion);
            return after;
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateTermException(exception);
        }
    }

    @Override public List<AuditEntry> history(String code, long offset, int size) {
        return jdbc.query("""
                SELECT a.id, a.action, a.before_snapshot::text, a.after_snapshot::text, a.reason,
                       a.changed_by_member_id, a.changed_at, a.version
                  FROM tag_definition_audit a JOIN tag_definition d ON d.id = a.tag_definition_id
                 WHERE d.tag_code = ? ORDER BY a.version DESC LIMIT ? OFFSET ?
                """, this::mapAudit, code, size, offset);
    }

    @Override public long historyCount(String code) {
        return jdbc.queryForObject("SELECT count(*) FROM tag_definition_audit a JOIN tag_definition d ON d.id = a.tag_definition_id WHERE d.tag_code = ?", Long.class, code);
    }

    private void lockTerms(List<String> terms) {
        terms.stream().sorted().forEach(term -> jdbc.queryForObject("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(?, 0))) locked", Integer.class, term));
    }
    private void insertTerms(UUID definitionId, List<String> terms, OffsetDateTime createdAt) {
        try {
            for (int index = 0; index < terms.size(); index++) jdbc.update("""
                    INSERT INTO tag_definition_term(id, tag_definition_id, term_kind, normalized_term, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), definitionId, index == 0 ? "DISPLAY_NAME" : "ALIAS", terms.get(index), createdAt);
        } catch (DataIntegrityViolationException exception) { throw new DuplicateTermException(exception); }
    }
    private TagDefinition map(ResultSet rs, int row) throws SQLException {
        try { return new TagDefinition(rs.getString("tag_code"), rs.getString("tag_type"), rs.getString("display_name"), mapper.readValue(rs.getString("aliases"), STRING_LIST), rs.getString("status"), rs.getString("source"), rs.getLong("version")); }
        catch (JacksonException exception) { throw new SQLException("Invalid tag aliases JSON.", exception); }
    }
    private LockedTag mapLocked(ResultSet rs, int row) throws SQLException { return new LockedTag(rs.getObject("id", UUID.class), map(rs, row)); }
    private AuditEntry mapAudit(ResultSet rs, int row) throws SQLException {
        try {
            UUID actor = rs.getObject("changed_by_member_id", UUID.class);
            return new AuditEntry(rs.getObject("id", UUID.class).toString(), rs.getString("action"), mapper.readValue(rs.getString("before_snapshot"), TagDefinition.class), mapper.readValue(rs.getString("after_snapshot"), TagDefinition.class), rs.getString("reason"), actor == null ? null : actor.toString(), rs.getObject("changed_at", OffsetDateTime.class), rs.getLong("version"));
        } catch (JacksonException exception) { throw new SQLException("Invalid tag definition audit JSON.", exception); }
    }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (JacksonException exception) { throw new IllegalStateException("Could not serialize tag definition JSON.", exception); } }
    private record LockedTag(UUID id, TagDefinition definition) { }
}
