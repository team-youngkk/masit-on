package com.masiton.ai.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase.AuditEntry;
import com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase.TagDefinition;
import com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase.MergePreview;
import com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase.MergeResult;
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
        if (jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM tag_definition_merge WHERE source_tag_definition_id = ?)",
                Boolean.class, locked.id())) throw new MergedSourceMutationException();
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

    @Override public MergePreview previewMerge(String sourceCode, String targetCode) {
        MergeState state = mergeState(sourceCode, targetCode, false);
        validateMergeState(state);
        return preview(state);
    }

    @Override public MergeResult merge(MergeChange change) {
        MergeState state = mergeState(change.sourceCode(), change.targetCode(), true);
        validateMergeState(state);
        if (state.source().definition().version() != change.expectedSourceVersion()
                || state.target().definition().version() != change.expectedTargetVersion()) {
            throw new ConcurrentUpdateException();
        }
        MergePreview preview = preview(state);
        if (!preview.previewFingerprint().equals(change.previewFingerprint())) {
            throw new StaleMergePreviewException();
        }

        UUID mergeId = UUID.randomUUID();
        TagDefinition sourceBefore = state.source().definition();
        TagDefinition sourceAfter = new TagDefinition(sourceBefore.code(), sourceBefore.type(),
                sourceBefore.displayName(), sourceBefore.aliases(), "DEPRECATED", sourceBefore.source(),
                sourceBefore.version() + 1);
        jdbc.update("""
                INSERT INTO tag_definition_merge(id, source_tag_definition_id, target_tag_definition_id,
                    source_before_snapshot, source_after_snapshot, target_snapshot,
                    source_version, target_version, preview_fingerprint,
                    reason, merged_by_member_id, merged_at, affected_visit_count,
                    moved_visit_tag_count, deduplicated_visit_tag_count)
                VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, mergeId, state.source().id(), state.target().id(), json(sourceBefore),
                json(sourceAfter), json(state.target().definition()), sourceBefore.version(), state.target().definition().version(),
                preview.previewFingerprint(), change.reason(), change.memberId(), change.changedAt(),
                preview.affectedVisitCount(), preview.movedVisitTagCount(), preview.deduplicatedVisitTagCount());

        for (VisitTagMergeRow row : state.rows()) {
            insertProvenance(mergeId, row.visitId(), row.sourceId(), "SOURCE", row.targetId() == null ? "MOVED" : "DEDUPLICATED",
                    row.sourceSnapshot(), change.changedAt());
            if (row.targetId() == null) {
                jdbc.update("UPDATE visit_tag SET tag_definition_id = ? WHERE id = ?", state.target().id(), row.sourceId());
            } else {
                insertProvenance(mergeId, row.visitId(), row.targetId(), "TARGET", "DEDUPLICATED",
                        row.targetSnapshot(), change.changedAt());
                jdbc.update("DELETE FROM visit_tag WHERE id = ?", row.sourceId());
            }
        }
        jdbc.update("UPDATE tag_definition SET status = 'DEPRECATED', version = ?, updated_at = ? WHERE id = ?",
                sourceAfter.version(), change.changedAt(), state.source().id());
        jdbc.update("""
                INSERT INTO tag_definition_audit(id, tag_definition_id, action, before_snapshot, after_snapshot,
                    reason, changed_by_member_id, changed_at, version)
                VALUES (?, ?, 'DEPRECATE', ?::jsonb, ?::jsonb, ?, ?, ?, ?)
                """, UUID.randomUUID(), state.source().id(), json(sourceBefore), json(sourceAfter), change.reason(),
                change.memberId(), change.changedAt(), sourceAfter.version());
        return new MergeResult(mergeId.toString(), sourceBefore.code(), state.target().definition().code(),
                sourceAfter.version(), state.target().definition().version(), preview.affectedVisitCount(),
                preview.movedVisitTagCount(), preview.deduplicatedVisitTagCount(), change.changedAt());
    }

    private MergeState mergeState(String sourceCode, String targetCode, boolean lock) {
        String suffix = lock ? " ORDER BY id FOR UPDATE" : " ORDER BY id";
        List<LockedTag> definitions = jdbc.query("SELECT id, " + COLUMNS
                + " FROM tag_definition WHERE tag_code IN (?, ?)" + suffix,
                this::mapLocked, sourceCode, targetCode);
        if (definitions.size() != 2) throw new NotFoundException();
        Map<String, LockedTag> byCode = new LinkedHashMap<>();
        definitions.forEach(value -> byCode.put(value.definition().code(), value));
        LockedTag source = byCode.get(sourceCode);
        LockedTag target = byCode.get(targetCode);
        if (source == null || target == null) throw new NotFoundException();
        if (lock) {
            jdbc.queryForList("""
                    SELECT id FROM visit_tag WHERE tag_definition_id IN (?, ?)
                    ORDER BY visit_id, tag_definition_id, id FOR UPDATE
                    """, UUID.class, source.id(), target.id());
        }
        List<VisitTagMergeRow> rows = jdbc.query("""
                SELECT s.id AS source_id, s.visit_id,
                       to_jsonb(s)::text AS source_snapshot,
                       t.id AS target_id, to_jsonb(t)::text AS target_snapshot
                  FROM visit_tag s
                  LEFT JOIN visit_tag t
                    ON t.visit_id = s.visit_id AND t.tag_definition_id = ?
                 WHERE s.tag_definition_id = ?
                 ORDER BY s.visit_id, s.id
                """, (rs, row) -> new VisitTagMergeRow(rs.getObject("source_id", UUID.class),
                rs.getObject("visit_id", UUID.class), rs.getString("source_snapshot"),
                rs.getObject("target_id", UUID.class), rs.getString("target_snapshot")), target.id(), source.id());
        boolean sourceMerged = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM tag_definition_merge WHERE source_tag_definition_id = ?)",
                Boolean.class, source.id());
        boolean cycle = jdbc.queryForObject("""
                WITH RECURSIVE chain(id) AS (
                    VALUES (?::uuid)
                    UNION
                    SELECT m.target_tag_definition_id
                      FROM tag_definition_merge m JOIN chain ON m.source_tag_definition_id = chain.id
                )
                SELECT EXISTS (SELECT 1 FROM chain WHERE id = ?)
                """, Boolean.class, target.id(), source.id());
        return new MergeState(source, target, rows, sourceMerged, cycle);
    }

    private void validateMergeState(MergeState state) {
        if (!state.source().definition().type().equals(state.target().definition().type())) {
            throw new InvalidMergeException("targetCode");
        }
        if (!"ACTIVE".equals(state.source().definition().status())
                || !"ACTIVE".equals(state.target().definition().status()) || state.sourceMerged() || state.cycle()) {
            throw new InvalidMergeException("TAG_DEFINITION_MERGE_CONFLICT");
        }
    }

    private MergePreview preview(MergeState state) {
        long deduplicated = state.rows().stream().filter(row -> row.targetId() != null).count();
        long affected = state.rows().size();
        return new MergePreview(state.source().definition(), state.target().definition(), affected,
                affected - deduplicated, deduplicated, fingerprint(state));
    }

    private String fingerprint(MergeState state) {
        StringBuilder value = new StringBuilder();
        appendDefinition(value, state.source());
        appendDefinition(value, state.target());
        state.rows().forEach(row -> value.append('|').append(row.visitId()).append(':')
                .append(row.sourceId()).append(':').append(row.sourceSnapshot()).append(':')
                .append(row.targetId()).append(':').append(row.targetSnapshot()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private void appendDefinition(StringBuilder value, LockedTag tag) {
        value.append(tag.id()).append(':').append(json(tag.definition()));
    }

    private void insertProvenance(UUID mergeId, UUID visitId, UUID visitTagId, String role,
                                  String outcome, String snapshot, OffsetDateTime recordedAt) {
        jdbc.update("""
                INSERT INTO visit_tag_merge_provenance(id, tag_definition_merge_id, visit_id, visit_tag_id,
                    snapshot_role, outcome, visit_tag_snapshot, recorded_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                """, UUID.randomUUID(), mergeId, visitId, visitTagId, role, outcome, snapshot, recordedAt);
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
    private record VisitTagMergeRow(UUID sourceId, UUID visitId, String sourceSnapshot,
                                    UUID targetId, String targetSnapshot) { }
    private record MergeState(LockedTag source, LockedTag target, List<VisitTagMergeRow> rows,
                              boolean sourceMerged, boolean cycle) { }
}
