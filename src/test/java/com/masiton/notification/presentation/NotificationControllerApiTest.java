package com.masiton.notification.presentation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.GlobalExceptionHandler;
import com.masiton.notification.application.port.in.NotificationItem;
import com.masiton.notification.application.port.in.NotificationPage;
import com.masiton.notification.application.port.in.NotificationReadAllResult;
import com.masiton.notification.application.port.in.NotificationReadResult;
import com.masiton.notification.application.port.in.NotificationUseCase;
import com.masiton.notification.domain.model.NotificationRequestType;
import com.masiton.notification.domain.model.NotificationStatus;
import com.masiton.security.infrastructure.web.MemberPrivateCacheFilter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("회원 알림 Controller API")
class NotificationControllerApiTest {

    private final NotificationUseCase useCase = mock(NotificationUseCase.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new NotificationController(useCase))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new MemberPrivateCacheFilter())
            .build();
    private final UUID memberId = UUID.randomUUID();

    @Test
    @DisplayName("알림 목록은 계약된 필드와 private no-store 헤더를 반환한다")
    void getNotifications_회원요청_목록계약과캐시헤더반환() throws Exception {
        UUID notificationId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-08-03T10:00:00+09:00");
        NotificationItem item = new NotificationItem(
                notificationId, NotificationRequestType.REPORT, reportId, NotificationStatus.IN_REVIEW,
                "신고 검토가 시작되었습니다.", "접수한 신고를 검토하고 있습니다.", false, null, createdAt);
        when(useCase.getNotifications(memberId, 1, 20))
                .thenReturn(new NotificationPage(List.of(item), 1, 20, 101, 6, true, 101));

        mockMvc.perform(get("/api/me/notifications").principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.items[0].notificationId").value(notificationId.toString()))
                .andExpect(jsonPath("$.items[0].type").value("REPORT_STATUS_CHANGED"))
                .andExpect(jsonPath("$.items[0].requestType").value("REPORT"))
                .andExpect(jsonPath("$.items[0].requestId").value(reportId.toString()))
                .andExpect(jsonPath("$.items[0].status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.items[0].title").value("신고 검토가 시작되었습니다."))
                .andExpect(jsonPath("$.items[0].message").value("접수한 신고를 검토하고 있습니다."))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.items[0].readAt").doesNotExist())
                .andExpect(jsonPath("$.unreadCount").value(101))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(101))
                .andExpect(jsonPath("$.page.totalPages").value(6))
                .andExpect(jsonPath("$.page.hasNext").value(true));
        // items[].read와 unreadCount가 서로 다른 트랜잭션에서 조립되지 않도록 목록 응답은
        // getNotifications 한 번의 결과에서만 만들어져야 한다(별도 getUnreadCount 재호출 금지).
        verify(useCase, never()).getUnreadCount(any());
    }

    @Test
    @DisplayName("허용하지 않는 페이지 크기는 오류 본문에도 private no-store 헤더를 반환한다")
    void getNotifications_허용하지않는크기_400과캐시헤더반환() throws Exception {
        mockMvc.perform(get("/api/me/notifications")
                        .principal(authentication())
                        .queryParam("size", "30"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    @DisplayName("정의되지 않은 쿼리 파라미터는 400 INVALID_REQUEST다")
    void getNotifications_정의되지않은파라미터_400INVALID_REQUEST() throws Exception {
        mockMvc.perform(get("/api/me/notifications")
                        .principal(authentication())
                        .queryParam("sort", "createdAt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("정확한 미읽음 수는 축약 없이 그대로 반환한다")
    void getUnreadCount_정확한수_그대로반환() throws Exception {
        when(useCase.getUnreadCount(memberId)).thenReturn(101);

        mockMvc.perform(get("/api/me/notifications/unread-count").principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.unreadCount").value(101));
    }

    @Test
    @DisplayName("개별 읽음은 이미 읽은 알림의 최초 readAt을 그대로 반환한다")
    void markAsRead_이미읽은알림_최초readAt반환() throws Exception {
        UUID notificationId = UUID.randomUUID();
        OffsetDateTime firstReadAt = OffsetDateTime.parse("2026-08-03T10:05:30+09:00");
        when(useCase.markAsRead(memberId, notificationId))
                .thenReturn(new NotificationReadResult(notificationId, true, firstReadAt));

        mockMvc.perform(put("/api/me/notifications/{notificationId}/read", notificationId)
                        .principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.notificationId").value(notificationId.toString()))
                .andExpect(jsonPath("$.read").value(true))
                .andExpect(jsonPath("$.readAt").value(firstReadAt.toString()));
    }

    @Test
    @DisplayName("없거나 다른 회원 소유인 알림은 404 NOTIFICATION_NOT_FOUND다")
    void markAsRead_없거나타회원소유_404NOTIFICATION_NOT_FOUND() throws Exception {
        UUID notificationId = UUID.randomUUID();
        when(useCase.markAsRead(memberId, notificationId)).thenThrow(
                new BusinessException(
                        HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "요청한 알림을 찾을 수 없습니다."));

        mockMvc.perform(put("/api/me/notifications/{notificationId}/read", notificationId)
                        .principal(authentication()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("형식이 잘못된 notificationId는 400이 아니라 404 NOTIFICATION_NOT_FOUND다")
    void markAsRead_형식오류식별자_400아닌404반환() throws Exception {
        mockMvc.perform(put("/api/me/notifications/{notificationId}/read", "not-a-uuid")
                        .principal(authentication()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("read-all 경로는 개별 읽음 경로와 겹치지 않고 전체 읽음으로 라우팅된다")
    void markAllAsRead_readAll경로_개별읽음과충돌없이라우팅() throws Exception {
        OffsetDateTime readAt = OffsetDateTime.parse("2026-08-03T10:05:30+09:00");
        when(useCase.markAllAsRead(memberId)).thenReturn(new NotificationReadAllResult(17, 0, readAt));

        mockMvc.perform(put("/api/me/notifications/read-all").principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.updatedCount").value(17))
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.readAt").value(readAt.toString()));
    }

    @Test
    @DisplayName("반복되는 전체 읽음 요청은 updatedCount 0인 200을 반환하고 readAt을 채운다")
    void markAllAsRead_반복요청_0건이어도readAt포함() throws Exception {
        OffsetDateTime readAt = OffsetDateTime.parse("2026-08-03T10:10:45+09:00");
        when(useCase.markAllAsRead(memberId)).thenReturn(new NotificationReadAllResult(0, 3, readAt));

        mockMvc.perform(put("/api/me/notifications/read-all").principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(0))
                .andExpect(jsonPath("$.unreadCount").value(3))
                .andExpect(jsonPath("$.readAt").value(readAt.toString()));
    }

    @Test
    @DisplayName("빈 알림 목록도 200과 빈 items를 반환한다")
    void getNotifications_결과없음_200과빈items반환() throws Exception {
        when(useCase.getNotifications(memberId, 1, 20))
                .thenReturn(new NotificationPage(List.of(), 1, 20, 0, 0, false, 0));

        mockMvc.perform(get("/api/me/notifications").principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return UsernamePasswordAuthenticationToken.authenticated(
                memberId.toString(), "N/A", List.of());
    }
}
