package com.masiton.notification.application;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.common.web.BusinessException;
import com.masiton.notification.application.port.in.NotificationPage;
import com.masiton.notification.application.port.in.NotificationReadAllResult;
import com.masiton.notification.application.port.in.NotificationReadResult;
import com.masiton.notification.application.port.out.NotificationQueryPort;
import com.masiton.notification.application.port.out.NotificationStore;
import com.masiton.notification.domain.model.NotificationCreationResult;
import com.masiton.notification.domain.model.NotificationRequestType;
import com.masiton.notification.domain.model.NotificationStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("회원 알림 서비스")
class NotificationServiceTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID NOTIFICATION_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-03T01:00:00Z");
    private static final OffsetDateTime NOW_OFFSET = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
    // ADR-DATA-012·데이터 계약 7절: 90일/최신 200개 보존 범위를 Application이 Clock에서 파생한다.
    private static final OffsetDateTime RETENTION_CUTOFF = NOW_OFFSET.minusDays(90);
    private static final int RETENTION_LIMIT = 200;

    private final NotificationStore store = mock(NotificationStore.class);
    private final NotificationQueryPort queries = mock(NotificationQueryPort.class);
    private final NotificationService service = new NotificationService(
            store, queries, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("목록 조회는 보존 cutoff·한도를 실어 query 포트 한 번만 호출한다")
    void getNotifications_보존cutoff와함께_query포트한번호출() {
        NotificationPage page = new NotificationPage(List.of(), 1, 20, 0, 0, false, 0);
        when(queries.findByMember(MEMBER_ID, RETENTION_CUTOFF, RETENTION_LIMIT, 1, 20)).thenReturn(page);

        NotificationPage result = service.getNotifications(MEMBER_ID, 1, 20);

        assertThat(result).isSameAs(page);
        verify(queries).findByMember(MEMBER_ID, RETENTION_CUTOFF, RETENTION_LIMIT, 1, 20);
        verify(queries, never()).countUnread(any(), any(), anyInt());
    }

    @Test
    @DisplayName("목록 응답의 unreadCount는 같은 페이지 결과에서 그대로 전달된다")
    void getNotifications_unreadCount_페이지결과값그대로전달() {
        NotificationPage page = new NotificationPage(List.of(), 1, 20, 101, 6, true, 101);
        when(queries.findByMember(eq(MEMBER_ID), any(OffsetDateTime.class), eq(RETENTION_LIMIT), eq(1), eq(20)))
                .thenReturn(page);

        NotificationPage result = service.getNotifications(MEMBER_ID, 1, 20);

        assertThat(result.unreadCount()).isEqualTo(101);
    }

    @Test
    @DisplayName("미읽음 수는 보존 cutoff·한도로 정확한 값을 그대로 반환하며 축약하지 않는다")
    void getUnreadCount_정확한수_축약없이반환() {
        when(queries.countUnread(MEMBER_ID, RETENTION_CUTOFF, RETENTION_LIMIT)).thenReturn(101);

        int unreadCount = service.getUnreadCount(MEMBER_ID);

        assertThat(unreadCount).isEqualTo(101);
    }

    @Test
    @DisplayName("개별 읽음 처리는 store의 최초 readAt을 그대로 반환한다")
    void markAsRead_이미읽은알림_최초readAt유지() {
        OffsetDateTime firstReadAt = OffsetDateTime.parse("2026-08-01T00:00:00Z");
        when(store.markAsRead(eq(MEMBER_ID), eq(NOTIFICATION_ID), any(OffsetDateTime.class),
                any(OffsetDateTime.class), eq(RETENTION_LIMIT)))
                .thenReturn(Optional.of(firstReadAt));

        NotificationReadResult result = service.markAsRead(MEMBER_ID, NOTIFICATION_ID);

        assertThat(result.notificationId()).isEqualTo(NOTIFICATION_ID);
        assertThat(result.read()).isTrue();
        assertThat(result.readAt()).isEqualTo(firstReadAt);
        verify(store).markAsRead(MEMBER_ID, NOTIFICATION_ID, NOW_OFFSET, RETENTION_CUTOFF, RETENTION_LIMIT);
    }

    @Test
    @DisplayName("없는 알림, 다른 회원 알림 또는 보존 범위 밖 알림은 404 NOTIFICATION_NOT_FOUND로 거부한다")
    void markAsRead_없거나다른회원소유또는보존범위밖_404로거부() {
        when(store.markAsRead(eq(MEMBER_ID), eq(NOTIFICATION_ID), any(OffsetDateTime.class),
                any(OffsetDateTime.class), eq(RETENTION_LIMIT)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(MEMBER_ID, NOTIFICATION_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).code())
                        .isEqualTo("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("전체 읽음은 updatedCount·unreadCount와 고정 시각을 함께 반환한다")
    void markAllAsRead_처리후_결과세값모두반환() {
        when(store.markAllAsRead(eq(MEMBER_ID), any(OffsetDateTime.class), any(OffsetDateTime.class), eq(RETENTION_LIMIT)))
                .thenReturn(17);
        when(queries.countUnread(eq(MEMBER_ID), any(OffsetDateTime.class), eq(RETENTION_LIMIT))).thenReturn(0);

        NotificationReadAllResult result = service.markAllAsRead(MEMBER_ID);

        assertThat(result.updatedCount()).isEqualTo(17);
        assertThat(result.unreadCount()).isZero();
        assertThat(result.readAt()).isEqualTo(NOW_OFFSET);
        verify(store).markAllAsRead(MEMBER_ID, NOW_OFFSET, RETENTION_CUTOFF, RETENTION_LIMIT);
        verify(queries).countUnread(MEMBER_ID, RETENTION_CUTOFF, RETENTION_LIMIT);
    }

    @Test
    @DisplayName("반복되는 전체 읽음 요청은 updatedCount 0이어도 readAt을 채운다")
    void markAllAsRead_반복요청_readAt항상채움() {
        when(store.markAllAsRead(eq(MEMBER_ID), any(OffsetDateTime.class), any(OffsetDateTime.class), eq(RETENTION_LIMIT)))
                .thenReturn(0);
        when(queries.countUnread(eq(MEMBER_ID), any(OffsetDateTime.class), eq(RETENTION_LIMIT))).thenReturn(3);

        NotificationReadAllResult result = service.markAllAsRead(MEMBER_ID);

        assertThat(result.updatedCount()).isZero();
        assertThat(result.unreadCount()).isEqualTo(3);
        assertThat(result.readAt()).isNotNull();
    }

    @Test
    @DisplayName("생성은 요청·상태 조합의 표시문을 서버가 구성해 저장소에 위임한다")
    void create_요청상태조합_고정표시문으로저장소호출() {
        when(store.insertIfAbsent(
                any(UUID.class), eq(MEMBER_ID), eq(NotificationRequestType.REPORT), eq(REQUEST_ID),
                eq(NotificationStatus.IN_REVIEW), anyString(), anyString(), any(OffsetDateTime.class)))
                .thenReturn(new NotificationCreationResult(NOTIFICATION_ID, true));

        NotificationCreationResult result = service.create(
                MEMBER_ID, NotificationRequestType.REPORT, REQUEST_ID, NotificationStatus.IN_REVIEW);

        assertThat(result.notificationId()).isEqualTo(NOTIFICATION_ID);
        assertThat(result.created()).isTrue();
        verify(store).insertIfAbsent(
                any(UUID.class), eq(MEMBER_ID), eq(NotificationRequestType.REPORT), eq(REQUEST_ID),
                eq(NotificationStatus.IN_REVIEW), eq("신고 검토가 시작되었습니다."),
                eq("접수한 신고를 검토하고 있습니다."), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("같은 요청·상태의 동시 생성 재시도는 기존 행으로 수렴한다")
    void create_중복충돌_기존행으로수렴() {
        when(store.insertIfAbsent(
                any(UUID.class), eq(MEMBER_ID), eq(NotificationRequestType.SUBMISSION), eq(REQUEST_ID),
                eq(NotificationStatus.ACCEPTED), anyString(), anyString(), any(OffsetDateTime.class)))
                .thenReturn(new NotificationCreationResult(NOTIFICATION_ID, false));

        NotificationCreationResult result = service.create(
                MEMBER_ID, NotificationRequestType.SUBMISSION, REQUEST_ID, NotificationStatus.ACCEPTED);

        assertThat(result.notificationId()).isEqualTo(NOTIFICATION_ID);
        assertThat(result.created()).isFalse();
    }

    @Test
    @DisplayName("memberId가 없으면 알림을 생성하지 않고 명확히 거부한다")
    void create_memberId없음_명확히거부() {
        assertThatThrownBy(() -> service.create(
                null, NotificationRequestType.REPORT, REQUEST_ID, NotificationStatus.IN_REVIEW))
                .isInstanceOf(IllegalArgumentException.class);

        verify(store, never()).insertIfAbsent(
                any(), any(), any(), any(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("생성은 호출자 트랜잭션 참여를 강제하는 MANDATORY 전파를 사용한다")
    void create_트랜잭션전파_MANDATORY() throws NoSuchMethodException {
        Method create = NotificationService.class.getMethod(
                "create", UUID.class, NotificationRequestType.class, UUID.class, NotificationStatus.class);

        Transactional transactional = create.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.MANDATORY);
    }

    @Test
    @DisplayName("목록·미읽음 수 조회는 읽기 전용 트랜잭션이다")
    void listQueries_readOnly트랜잭션() throws NoSuchMethodException {
        Method getNotifications = NotificationService.class
                .getMethod("getNotifications", UUID.class, int.class, int.class);
        Method getUnreadCount = NotificationService.class.getMethod("getUnreadCount", UUID.class);

        assertThat(getNotifications.getAnnotation(Transactional.class).readOnly()).isTrue();
        assertThat(getUnreadCount.getAnnotation(Transactional.class).readOnly()).isTrue();
    }

    @Test
    @DisplayName("읽음 처리 메서드는 쓰기 트랜잭션을 갖는다")
    void readCommands_쓰기트랜잭션() throws NoSuchMethodException {
        Method markAsRead = NotificationService.class.getMethod("markAsRead", UUID.class, UUID.class);
        Method markAllAsRead = NotificationService.class.getMethod("markAllAsRead", UUID.class);

        assertThat(markAsRead.getAnnotation(Transactional.class)).isNotNull();
        assertThat(markAsRead.getAnnotation(Transactional.class).readOnly()).isFalse();
        assertThat(markAllAsRead.getAnnotation(Transactional.class)).isNotNull();
        assertThat(markAllAsRead.getAnnotation(Transactional.class).readOnly()).isFalse();
    }
}
