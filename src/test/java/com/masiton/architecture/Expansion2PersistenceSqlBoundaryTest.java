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

/**
 * 2차 확장 도메인({@code curation}, {@code participation}, {@code notification})의
 * {@code infrastructure/persistence} SQL 문자열이 그 도메인이 소유한 테이블로만 한정되는지
 * 검증한다. {@link PersonalPersistenceSqlBoundaryTest}와 같은 정규식 스캔 방식을 쓴다.
 *
 * <p>도메인별 소유 테이블 근거는
 * {@code docs/05-specs/data/second-expansion-data-contract.md} 5~7절과
 * {@code src/main/resources/db/migration/V3__add_expansion_2_schema.sql}이다.
 */
@DisplayName("2차 확장 도메인 persistence SQL 경계")
class Expansion2PersistenceSqlBoundaryTest {

    private static final Pattern TABLE_REFERENCE = Pattern.compile(
            "(?i)\\b(?:from|join|into|update(?!\\s+set\\b))\\s+([a-z_][a-z0-9_]*)");

    private static final Path CURATION_PERSISTENCE = Path.of(
            "src/main/java/com/masiton/curation/infrastructure/persistence");
    private static final Path PARTICIPATION_PERSISTENCE = Path.of(
            "src/main/java/com/masiton/participation/infrastructure/persistence");
    private static final Path NOTIFICATION_PERSISTENCE = Path.of(
            "src/main/java/com/masiton/notification/infrastructure/persistence");

    // 근거: docs/05-specs/data/second-expansion-data-contract.md 5.1~5.2절 (WS-11 소유),
    // src/main/resources/db/migration/V3__add_expansion_2_schema.sql 36~77행
    private static final Set<String> CURATION_ALLOWED_TABLES = Set.of(
            "curation",
            "curation_restaurant");

    // 근거: docs/05-specs/data/second-expansion-data-contract.md 6.2~6.4절 (WS-12 소유),
    // src/main/resources/db/migration/V3__add_expansion_2_schema.sql 82~268행
    // `member_account`는 lockMember에서 회원당 제보·신고 생성 동시성 행 락(FOR UPDATE)을 위해 참조한다.
    private static final Set<String> PARTICIPATION_ALLOWED_TABLES = Set.of(
            "submission",
            "report",
            "moderation_history",
            "member_account");

    // 근거: docs/05-specs/data/second-expansion-data-contract.md 7.1절 (WS-13 소유),
    // src/main/resources/db/migration/V3__add_expansion_2_schema.sql 273~299행
    //
    // "ranked"는 실제 테이블이 아니라 JdbcNotificationAdapter의 `WITH ranked AS (...)` CTE
    // 이름이다. `UPDATE notification SET ... FROM ranked`, `SELECT ... FROM ranked` 구문에서
    // 정규식이 FROM/UPDATE 뒤 식별자를 테이블로 포착하므로, CTE 참조를 실제 타 도메인 테이블
    // 참조로 오탐하지 않도록 이 스캔 범위 안에서만 명시적으로 허용한다.
    private static final Set<String> NOTIFICATION_ALLOWED_TABLES = Set.of(
            "notification",
            "ranked");

    @Test
    @DisplayName("Curation persistence SQL은 허용된 테이블만 참조한다")
    void curationPersistenceSql_운영소스_타도메인테이블을참조하지않는다() throws IOException {
        assertReferencedTablesAreAllowed(CURATION_PERSISTENCE, CURATION_ALLOWED_TABLES);
    }

    @Test
    @DisplayName("Participation persistence SQL은 허용된 테이블만 참조한다")
    void participationPersistenceSql_운영소스_타도메인테이블을참조하지않는다() throws IOException {
        assertReferencedTablesAreAllowed(PARTICIPATION_PERSISTENCE, PARTICIPATION_ALLOWED_TABLES);
    }

    @Test
    @DisplayName("Notification persistence SQL은 허용된 테이블만 참조한다")
    void notificationPersistenceSql_운영소스_타도메인테이블을참조하지않는다() throws IOException {
        assertReferencedTablesAreAllowed(NOTIFICATION_PERSISTENCE, NOTIFICATION_ALLOWED_TABLES);
    }

    @Test
    @DisplayName("SQL 테이블 추출은 JOIN·upsert·CTE를 구분해 실제 테이블만 찾는다")
    void tableReferencePattern_joinUpsertCte_실제테이블만찾는다() {
        Set<String> referencedTables = referencedTables("""
                WITH ranked AS (
                    SELECT id FROM notification WHERE member_id = ?
                )
                SELECT id FROM ranked
                JOIN report r ON r.id = ranked.report_id
                INSERT INTO submission (id) VALUES (?)
                ON CONFLICT (id) DO UPDATE SET status = ?
                """);

        assertThat(referencedTables)
                .containsExactly("notification", "ranked", "report", "submission");
    }

    private void assertReferencedTablesAreAllowed(Path persistencePackage, Set<String> allowedTables)
            throws IOException {
        try (var sources = Files.list(persistencePackage)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                Set<String> referencedTables = referencedTables(Files.readString(source));
                assertThat(referencedTables)
                        .as("%s가 참조한 SQL 테이블", source)
                        .isSubsetOf(allowedTables);
            }
        }
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
