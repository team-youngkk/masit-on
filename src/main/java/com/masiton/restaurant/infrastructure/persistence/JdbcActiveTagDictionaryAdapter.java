package com.masiton.restaurant.infrastructure.persistence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.masiton.restaurant.application.port.out.ActiveTagDictionaryPort;
import com.masiton.restaurant.application.port.out.ActiveTagDictionaryPort.ActiveTagDictionarySnapshot;
import com.masiton.restaurant.application.port.out.ActiveTagDictionaryPort.TagDefinition;
import com.masiton.restaurant.application.port.out.ActiveTagDictionaryUnavailableException;

/** tag_definition 정본을 읽고 30초 동안 프로세스 내 snapshot으로 공유한다. */
@Repository
public final class JdbcActiveTagDictionaryAdapter implements ActiveTagDictionaryPort {

    static final Duration DEFAULT_TTL = Duration.ofSeconds(30);

    private static final String SELECT_ACTIVE_TERMS = """
            SELECT definition.tag_code, term.normalized_term
              FROM tag_definition definition
              JOIN tag_definition_term term
                ON term.tag_definition_id = definition.id
             WHERE definition.status = 'ACTIVE'
             ORDER BY definition.tag_code, term.normalized_term
            """;

    private final TagDictionaryRowLoader rowLoader;
    private final Clock clock;
    private final Duration ttl;
    private final ReentrantLock refreshLock = new ReentrantLock();

    private volatile CachedSnapshot cachedSnapshot;

    @Autowired
    public JdbcActiveTagDictionaryAdapter(JdbcTemplate jdbcTemplate) {
        this(() -> jdbcTemplate.query(
                SELECT_ACTIVE_TERMS,
                (resultSet, rowNumber) -> new TagTermRow(
                        resultSet.getString("tag_code"), resultSet.getString("normalized_term"))),
                Clock.systemUTC(), DEFAULT_TTL);
    }

    JdbcActiveTagDictionaryAdapter(TagDictionaryRowLoader rowLoader, Clock clock, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        this.rowLoader = rowLoader;
        this.clock = clock;
        this.ttl = ttl;
    }

    @Override
    public ActiveTagDictionarySnapshot getActiveTagDictionary() {
        Instant now = clock.instant();
        CachedSnapshot current = cachedSnapshot;
        if (isFresh(current, now)) {
            return current.snapshot();
        }

        refreshLock.lock();
        try {
            now = clock.instant();
            current = cachedSnapshot;
            if (isFresh(current, now)) {
                return current.snapshot();
            }

            ActiveTagDictionarySnapshot refreshed = loadSnapshot();
            cachedSnapshot = new CachedSnapshot(refreshed, clock.instant().plus(ttl));
            return refreshed;
        } finally {
            refreshLock.unlock();
        }
    }

    private boolean isFresh(CachedSnapshot candidate, Instant now) {
        return candidate != null && now.isBefore(candidate.expiresAt());
    }

    private ActiveTagDictionarySnapshot loadSnapshot() {
        try {
            List<TagTermRow> rows = rowLoader.load();
            List<TagDefinition> definitions = new ArrayList<>();
            String currentCode = null;
            List<String> currentTerms = null;
            for (TagTermRow row : rows) {
                if (!row.code().equals(currentCode)) {
                    if (currentCode != null) {
                        definitions.add(new TagDefinition(currentCode, currentTerms));
                    }
                    currentCode = row.code();
                    currentTerms = new ArrayList<>();
                }
                currentTerms.add(row.term());
            }
            if (currentCode != null) {
                definitions.add(new TagDefinition(currentCode, currentTerms));
            }
            return new ActiveTagDictionarySnapshot(definitions);
        } catch (RuntimeException exception) {
            throw new ActiveTagDictionaryUnavailableException(exception);
        }
    }

    @FunctionalInterface
    interface TagDictionaryRowLoader {
        List<TagTermRow> load();
    }

    record TagTermRow(String code, String term) {
    }

    private record CachedSnapshot(ActiveTagDictionarySnapshot snapshot, Instant expiresAt) {
    }
}
