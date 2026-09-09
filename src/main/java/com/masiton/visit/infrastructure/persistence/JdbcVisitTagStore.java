package com.masiton.visit.infrastructure.persistence;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.visit.application.port.in.ManageVisitTagsUseCase.Change;
import com.masiton.visit.application.port.in.ManageVisitTagsUseCase.Item;
import com.masiton.visit.application.port.in.ManageVisitTagsUseCase.Result;
import com.masiton.visit.application.port.in.ManageVisitTagsUseCase.Tag;
import com.masiton.visit.application.port.in.ManageVisitTagsUseCase.TagOption;
import com.masiton.visit.application.port.out.VisitTagStore;

@Repository
public class JdbcVisitTagStore implements VisitTagStore {
    private static final String VALID_VISITS = """
            FROM visit v JOIN restaurant r ON r.id = v.restaurant_id
            JOIN creator c ON c.id = v.creator_id JOIN video vi ON vi.id = v.video_id
            WHERE r.id = ? AND r.publication_status = 'PUBLIC' AND r.lifecycle_status = 'ACTIVE'
              AND v.publication_status = 'PUBLIC' AND v.lifecycle_status = 'ACTIVE'
              AND c.publication_status = 'PUBLIC' AND c.lifecycle_status = 'ACTIVE'
              AND c.external_availability_status = 'AVAILABLE'
              AND vi.publication_status = 'PUBLIC' AND vi.lifecycle_status = 'ACTIVE'
              AND vi.external_availability_status = 'AVAILABLE'
            """;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcVisitTagStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public Result list(UUID restaurantId) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM restaurant WHERE id = ?
                AND publication_status = 'PUBLIC' AND lifecycle_status = 'ACTIVE')
                """, Boolean.class, restaurantId))) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        List<Item> items = jdbc.query("SELECT v.id, c.channel_name, vi.title, vi.source_url "
                        + VALID_VISITS + " ORDER BY v.id",
                (rs, row) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    return new Item(id.toString(), rs.getString("channel_name"), rs.getString("title"),
                            rs.getString("source_url"), version(id), tags(id));
                }, restaurantId);
        List<TagOption> options = jdbc.query("""
                SELECT tag_code, display_name, tag_type FROM tag_definition
                WHERE status = 'ACTIVE' ORDER BY tag_type, tag_code
                """, (rs, row) -> new TagOption(rs.getString(1), rs.getString(2), rs.getString(3)));
        return new Result(items, options);
    }

    @Override
    public void replace(UUID restaurantId, UUID visitId, Change change, UUID memberId) {
        List<String> observedBefore = tags(visitId).stream().map(Tag::code).toList();
        List<String> requested = change.tagCodes().stream().sorted().toList();
        Map<String, LockedDefinition> definitions = lockDefinitions(Stream.concat(
                observedBefore.stream(), requested.stream()).distinct().sorted().toList());

        // Tag definitions are always locked before the Visit row. A merge takes the same definition locks
        // exclusively, so a replace that raced with a merge rechecks its token after the merge commits.
        if (jdbc.query("SELECT v.id " + VALID_VISITS + " AND v.id = ? FOR UPDATE OF v",
                (rs, row) -> rs.getObject(1, UUID.class), restaurantId, visitId).isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!version(visitId).equals(change.expectedVersion())) {
            throw concurrentUpdate();
        }
        List<String> before = tags(visitId).stream().map(Tag::code).sorted().toList();
        if (!definitions.keySet().containsAll(before)) {
            throw concurrentUpdate();
        }
        // Existing connections can be retained after deprecation; only newly added codes require ACTIVE.
        var selectedDefinitions = new LinkedHashMap<String, UUID>();
        for (String code : requested) {
            LockedDefinition definition = definitions.get(code);
            if (definition == null || (!before.contains(code) && !"ACTIVE".equals(definition.status()))) {
                throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "tagCodes", "활성 태그를 선택해 주세요.");
            }
            selectedDefinitions.put(code, definition.id());
        }
        List<String> after = requested;
        if (before.equals(after)) {
            return;
        }
        for (String code : before) {
            if (!selectedDefinitions.containsKey(code)) {
                jdbc.update("""
                        DELETE FROM visit_tag WHERE visit_id = ? AND tag_definition_id =
                        (SELECT id FROM tag_definition WHERE tag_code = ?)
                        """, visitId, code);
            }
        }
        selectedDefinitions.forEach((code, definitionId) -> {
            if (!before.contains(code)) {
                jdbc.update("""
                        INSERT INTO visit_tag(id, visit_id, tag_definition_id, source, evidence)
                        VALUES (?, ?, ?, 'ADMIN_OVERRIDE', '{"type":"UNKNOWN"}'::jsonb)
                        """, UUID.randomUUID(), visitId, definitionId);
            }
        });
        jdbc.update("""
                INSERT INTO visit_tag_revision(id, visit_id, revision, before_tag_codes, after_tag_codes,
                                               reason, changed_by_member_id)
                VALUES (?, ?, (SELECT COALESCE(MAX(revision), 0) + 1 FROM visit_tag_revision WHERE visit_id = ?),
                        ?::jsonb, ?::jsonb, ?, ?)
                """, UUID.randomUUID(), visitId, visitId, mapper.writeValueAsString(before),
                mapper.writeValueAsString(after), change.reason(), memberId);
    }

    private Map<String, LockedDefinition> lockDefinitions(List<String> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(codes.size(), "?"));
        Map<String, LockedDefinition> result = new LinkedHashMap<>();
        jdbc.query("SELECT id, tag_code, status FROM tag_definition WHERE tag_code IN (" + placeholders
                        + ") ORDER BY id FOR SHARE",
                rs -> {
                    LockedDefinition definition = new LockedDefinition(
                            rs.getObject("id", UUID.class), rs.getString("status"));
                    result.put(rs.getString("tag_code"), definition);
                }, codes.toArray());
        return result;
    }

    private BusinessException concurrentUpdate() {
        return new BusinessException(HttpStatus.CONFLICT, "VISIT_TAG_CONCURRENT_UPDATE",
                "다른 변경이 있습니다. 최신 태그를 조회한 후 다시 수정해 주세요.");
    }

    private List<Tag> tags(UUID visitId) {
        return jdbc.query("""
                SELECT td.tag_code, td.display_name, td.tag_type, vt.source
                FROM visit_tag vt JOIN tag_definition td ON td.id = vt.tag_definition_id
                WHERE vt.visit_id = ? ORDER BY td.tag_code
                """, (rs, row) -> new Tag(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)), visitId);
    }

    private String version(UUID visitId) {
        // Row identity and evidence detect remove/reinsert and writes made through AI workflows, too.
        return jdbc.queryForObject("""
                SELECT md5(?::text || ':' ||
                    COALESCE((SELECT jsonb_agg(to_jsonb(vt) || jsonb_build_object('code', td.tag_code)
                                             ORDER BY vt.id)::text
                              FROM visit_tag vt JOIN tag_definition td ON td.id = vt.tag_definition_id
                              WHERE vt.visit_id = ?), '[]') || ':' ||
                    (SELECT COALESCE(MAX(revision), 0)::text FROM visit_tag_revision WHERE visit_id = ?))
                """, String.class, visitId, visitId, visitId);
    }

    private record LockedDefinition(UUID id, String status) { }
}
