package com.masiton.notification.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.masiton.notification.application.port.in.NotificationItem;
import com.masiton.notification.application.port.in.NotificationPage;
import com.masiton.notification.application.port.out.NotificationQueryPort;
import com.masiton.notification.application.port.out.NotificationStore;
import com.masiton.notification.domain.model.NotificationCreationResult;
import com.masiton.notification.domain.model.NotificationRequestType;
import com.masiton.notification.domain.model.NotificationStatus;

/**
 * 알림은 다른 도메인 테이블과 조인 없이 자신의 표시 필드(title/message/status)를 그대로 갖고
 * 있으므로 orchestration 없이 이 도메인 Adapter가 Command·Query Port를 함께 구현한다.
 *
 * <p>보존 판정은 {@code JdbcRetentionCleanupAdapter.deleteExpiredNotifications}의 삭제 조건
 * {@code created_at < cutoff AND member_rank > 200}의 여집합이어야 한다(ADR-DATA-012 5·7절,
 * 데이터 계약 7절). 따라서 이 Adapter의 모든 조회·읽음 경로는 같은 순위 정의로 계산한
 * {@code created_at >= cutoff OR member_rank <= retentionLimit}를 공통으로 적용한다.
 */
@Repository
public class JdbcNotificationAdapter implements NotificationStore, NotificationQueryPort {

    /**
     * cleanup의 순위 계산과 동일하게 {@code ORDER BY created_at DESC, id DESC}로 최신 200건을
     * 정의한다. 목록 응답의 최종 정렬은 계약대로 {@code created_at DESC, id ASC}를 유지하므로
     * 두 정렬의 tie-break 방향이 다른 것은 의도된 것이다(순위 정의 일치 vs 응답 안정 정렬 일치).
     */
    private static final String RANKED_CTE = """
            WITH ranked AS (
                SELECT id, submission_id, report_id, status, title, message, read_at, created_at,
                       row_number() OVER (ORDER BY created_at DESC, id DESC) AS member_rank
                  FROM notification
                 WHERE member_id = ?
            )
            """;

    // notification.created_at과 이름이 겹치므로 UPDATE ... FROM ranked 문맥에서도 모호하지 않도록
    // 항상 ranked. 로 한정한다.
    private static final String RETENTION_PREDICATE = "(ranked.created_at >= ? OR ranked.member_rank <= ?)";

    private final JdbcTemplate jdbcTemplate;

    public JdbcNotificationAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public NotificationCreationResult insertIfAbsent(
            UUID id,
            UUID memberId,
            NotificationRequestType requestType,
            UUID requestId,
            NotificationStatus status,
            String title,
            String message,
            OffsetDateTime createdAt
    ) {
        UUID submissionId = requestType == NotificationRequestType.SUBMISSION ? requestId : null;
        UUID reportId = requestType == NotificationRequestType.REPORT ? requestId : null;
        // ux_notification__submission_status/ux_notification__report_status는 partial unique index이므로
        // ON CONFLICT 추론이 인덱스와 일치하도록 같은 WHERE 조건을 명시해야 한다.
        String conflictTarget = requestType == NotificationRequestType.SUBMISSION
                ? "(submission_id, status) WHERE submission_id IS NOT NULL"
                : "(report_id, status) WHERE report_id IS NOT NULL";
        List<UUID> inserted = jdbcTemplate.query(
                "INSERT INTO notification "
                        + "(id, member_id, submission_id, report_id, status, title, message, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT " + conflictTarget + " DO NOTHING RETURNING id",
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                id, memberId, submissionId, reportId, status.name(), title, message, createdAt);
        if (!inserted.isEmpty()) {
            return new NotificationCreationResult(inserted.get(0), true);
        }
        UUID existingId = findExisting(memberId, requestType, requestId, status)
                .orElseThrow(() -> new IllegalStateException(
                        "중복 충돌 뒤 기존 알림을 찾지 못했습니다: " + requestType + " " + status));
        return new NotificationCreationResult(existingId, false);
    }

    @Override
    public Optional<OffsetDateTime> markAsRead(
            UUID memberId,
            UUID notificationId,
            OffsetDateTime readAt,
            OffsetDateTime retentionCutoff,
            int retentionLimit
    ) {
        List<OffsetDateTime> updated = jdbcTemplate.query(
                RANKED_CTE
                        + "UPDATE notification SET read_at = ? "
                        + "FROM ranked "
                        + "WHERE notification.id = ranked.id "
                        + "  AND notification.id = ? "
                        + "  AND notification.member_id = ? "
                        + "  AND notification.read_at IS NULL "
                        + "  AND " + RETENTION_PREDICATE + " "
                        + "RETURNING notification.read_at",
                (resultSet, rowNumber) -> resultSet.getObject("read_at", OffsetDateTime.class),
                memberId, readAt, notificationId, memberId, retentionCutoff, retentionLimit);
        if (!updated.isEmpty()) {
            return Optional.of(updated.get(0));
        }
        List<OffsetDateTime> existing = jdbcTemplate.query(
                RANKED_CTE
                        + "SELECT read_at FROM ranked "
                        + "WHERE id = ? AND " + RETENTION_PREDICATE,
                (resultSet, rowNumber) -> resultSet.getObject("read_at", OffsetDateTime.class),
                memberId, notificationId, retentionCutoff, retentionLimit);
        return existing.stream().findFirst();
    }

    @Override
    public int markAllAsRead(
            UUID memberId, OffsetDateTime requestTime, OffsetDateTime retentionCutoff, int retentionLimit
    ) {
        return jdbcTemplate.update(
                RANKED_CTE
                        + "UPDATE notification SET read_at = ? "
                        + "FROM ranked "
                        + "WHERE notification.id = ranked.id "
                        + "  AND notification.member_id = ? "
                        + "  AND notification.read_at IS NULL "
                        + "  AND notification.created_at <= ? "
                        + "  AND " + RETENTION_PREDICATE,
                memberId, requestTime, memberId, requestTime, retentionCutoff, retentionLimit);
    }

    @Override
    public NotificationPage findByMember(
            UUID memberId, OffsetDateTime retentionCutoff, int retentionLimit, int page, int size
    ) {
        long offset = ((long) page - 1) * size;
        // items와 totalElements·unreadCount를 한 문장에서 얻는다. 읽기 전용 트랜잭션이라도 기본
        // 격리 수준은 문장마다 새 스냅숏을 보므로, 문장을 나누면 같은 응답 안에서 items의 read
        // 상태와 unreadCount가 서로 다른 시점의 사실이 될 수 있다. 창 함수는 LIMIT 적용 전
        // 보존 범위 전체를 세므로 페이지 단위 값이 아니다.
        List<PagedRow> rows = jdbcTemplate.query(
                RANKED_CTE
                        + "SELECT id, submission_id, report_id, status, title, message, read_at, created_at, "
                        + "       count(*) OVER () AS total_count, "
                        + "       count(*) FILTER (WHERE read_at IS NULL) OVER () AS unread_count "
                        + "  FROM ranked "
                        + " WHERE " + RETENTION_PREDICATE
                        + " ORDER BY created_at DESC, id ASC "
                        + " LIMIT ? OFFSET ?",
                (resultSet, rowNumber) -> new PagedRow(
                        mapItem(resultSet, rowNumber),
                        resultSet.getLong("total_count"),
                        resultSet.getInt("unread_count")),
                memberId, retentionCutoff, retentionLimit, size, offset);
        // 결과 범위를 벗어난 페이지는 행이 없어 창 함수 값을 얻을 수 없다. 이때는 어긋날 items가
        // 없으므로 집계만 따로 센다.
        Counts counts = rows.isEmpty()
                ? counts(memberId, retentionCutoff, retentionLimit)
                : new Counts(rows.getFirst().total(), rows.getFirst().unread());
        List<NotificationItem> items = rows.stream().map(PagedRow::item).toList();
        int pages = counts.total() == 0 ? 0 : (int) ((counts.total() + size - 1) / size);
        return new NotificationPage(items, page, size, counts.total(), pages, page < pages, counts.unread());
    }

    @Override
    public int countUnread(UUID memberId, OffsetDateTime retentionCutoff, int retentionLimit) {
        return counts(memberId, retentionCutoff, retentionLimit).unread();
    }

    /** totalElements와 unreadCount를 한 SELECT에서 계산해 같은 스냅숏의 값이 되게 한다. */
    private Counts counts(UUID memberId, OffsetDateTime retentionCutoff, int retentionLimit) {
        return jdbcTemplate.query(
                RANKED_CTE
                        + "SELECT count(*) AS total, count(*) FILTER (WHERE read_at IS NULL) AS unread "
                        + "  FROM ranked "
                        + " WHERE " + RETENTION_PREDICATE,
                (resultSet, rowNumber) -> new Counts(resultSet.getLong("total"), resultSet.getInt("unread")),
                memberId, retentionCutoff, retentionLimit)
                .stream().findFirst().orElse(new Counts(0, 0));
    }

    private Optional<UUID> findExisting(
            UUID memberId, NotificationRequestType requestType, UUID requestId, NotificationStatus status
    ) {
        String column = requestType == NotificationRequestType.SUBMISSION ? "submission_id" : "report_id";
        List<UUID> rows = jdbcTemplate.query(
                "SELECT id FROM notification WHERE " + column + " = ? AND status = ? AND member_id = ?",
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                requestId, status.name(), memberId);
        return rows.stream().findFirst();
    }

    private NotificationItem mapItem(ResultSet resultSet, int rowNumber) throws SQLException {
        UUID submissionId = resultSet.getObject("submission_id", UUID.class);
        UUID reportId = resultSet.getObject("report_id", UUID.class);
        NotificationRequestType requestType = submissionId != null
                ? NotificationRequestType.SUBMISSION
                : NotificationRequestType.REPORT;
        UUID requestId = submissionId != null ? submissionId : reportId;
        OffsetDateTime readAt = resultSet.getObject("read_at", OffsetDateTime.class);
        return new NotificationItem(
                resultSet.getObject("id", UUID.class),
                requestType,
                requestId,
                NotificationStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("title"),
                resultSet.getString("message"),
                readAt != null,
                readAt,
                resultSet.getObject("created_at", OffsetDateTime.class));
    }

    private record Counts(long total, int unread) {
    }

    private record PagedRow(NotificationItem item, long total, int unread) {
    }
}
