package com.masiton.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Personal persistence SQL 경계")
class PersonalPersistenceSqlBoundaryTest {

    private static final Path PERSONAL_PERSISTENCE = Path.of(
            "src/main/java/com/masiton/personal/infrastructure/persistence");
    private static final Pattern TABLE_REFERENCE = Pattern.compile(
            "(?i)\\b(?:from|join|into|update(?!\\s+set\\b))\\s+([a-z_][a-z0-9_]*)");
    private static final Set<String> ALLOWED_TABLES = Set.of(
            "personal_collection",
            "collection_restaurant",
            "favorite",
            "recent_restaurant_view");

    @Test
    @DisplayName("Personal persistence SQL은 허용된 테이블만 참조한다")
    void personalPersistenceSql_운영소스_타도메인테이블을참조하지않는다() throws IOException {
        Set<String> referencedTables = new LinkedHashSet<>();

        try (var sources = Files.list(PERSONAL_PERSISTENCE)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                referencedTables.addAll(referencedTables(Files.readString(source)));
            }
        }

        assertThat(referencedTables)
                .as("Personal persistence가 참조한 SQL 테이블")
                .isSubsetOf(ALLOWED_TABLES);
    }

    @Test
    @DisplayName("SQL 테이블 추출은 Restaurant JOIN을 찾고 upsert 구문은 오탐하지 않는다")
    void tableReferencePattern_restaurantJoin과Upsert_실제테이블만찾는다() {
        Set<String> referencedTables = referencedTables("""
                SELECT cr.restaurant_id
                  FROM collection_restaurant cr
                  JOIN restaurant r ON r.id = cr.restaurant_id
                INSERT INTO favorite (member_id, restaurant_id)
                VALUES (?, ?) ON CONFLICT (member_id, restaurant_id) DO UPDATE
                SET favorited_at = CURRENT_TIMESTAMP
                """);

        assertThat(referencedTables)
                .containsExactly("collection_restaurant", "restaurant", "favorite");
    }

    private Set<String> referencedTables(String source) {
        Set<String> referencedTables = new LinkedHashSet<>();
        Matcher matcher = TABLE_REFERENCE.matcher(source);
        while (matcher.find()) {
            referencedTables.add(matcher.group(1).toLowerCase());
        }
        return referencedTables;
    }
}
