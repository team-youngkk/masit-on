package com.masiton.notification.presentation;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.notification.application.port.in.NotificationPage;
import com.masiton.notification.application.port.in.NotificationReadAllResult;
import com.masiton.notification.application.port.in.NotificationReadResult;
import com.masiton.notification.application.port.in.NotificationUseCase;

@RestController
@RequestMapping("/api/me/notifications")
public class NotificationController {

    private static final Set<String> PAGE_FIELDS = Set.of("page", "size");
    private static final Set<Integer> ALLOWED_SIZES = Set.of(10, 20, 50);
    private static final String PRIVATE_NO_STORE = "private, no-store";

    private final NotificationUseCase useCase;

    public NotificationController(NotificationUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public ResponseEntity<NotificationResponse.NotificationList> getNotifications(
            Authentication authentication,
            @RequestParam MultiValueMap<String, String> query
    ) {
        PageRequest pageRequest = page(query);
        UUID memberId = memberId(authentication);
        NotificationPage result = useCase.getNotifications(memberId, pageRequest.number, pageRequest.size);
        return privateResponse(NotificationResponse.NotificationList.from(result));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<NotificationResponse.UnreadCount> getUnreadCount(Authentication authentication) {
        int unreadCount = useCase.getUnreadCount(memberId(authentication));
        return privateResponse(new NotificationResponse.UnreadCount(unreadCount));
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse.ReadState> markAsRead(
            Authentication authentication, @PathVariable String notificationId
    ) {
        UUID id = notificationId(notificationId);
        NotificationReadResult result = useCase.markAsRead(memberId(authentication), id);
        return privateResponse(NotificationResponse.ReadState.from(result));
    }

    @PutMapping("/read-all")
    public ResponseEntity<NotificationResponse.ReadAllState> markAllAsRead(Authentication authentication) {
        NotificationReadAllResult result = useCase.markAllAsRead(memberId(authentication));
        return privateResponse(NotificationResponse.ReadAllState.from(result));
    }

    private <T> ResponseEntity<T> privateResponse(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, PRIVATE_NO_STORE).body(body);
    }

    private UUID memberId(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "인증이 필요합니다.");
        }
    }

    /**
     * 외부 식별자는 불투명 문자열이며 클라이언트는 UUID 여부를 검증하지 않는다. 따라서 형식이
     * 맞지 않는 값도 다른 회원 소유·미존재 알림과 같은 404로 표현하고 400으로 유출하지 않는다.
     */
    private UUID notificationId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "요청한 알림을 찾을 수 없습니다.");
        }
    }

    private PageRequest page(MultiValueMap<String, String> query) {
        for (String field : query.keySet()) {
            if (!PAGE_FIELDS.contains(field)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            List<String> values = query.get(field);
            if (values != null && values.size() > 1) {
                throw new BusinessException(
                        ErrorCode.INVALID_FIELD_VALUE, field, "값은 한 번만 지정할 수 있습니다.");
            }
        }
        int number = integer(query.getFirst("page"), "page", 1);
        int size = integer(query.getFirst("size"), "size", 20);
        if (number < 1) {
            throw new BusinessException(
                    ErrorCode.INVALID_FIELD_VALUE, "page", "1 이상의 값만 허용합니다.");
        }
        if (!ALLOWED_SIZES.contains(size)) {
            throw new BusinessException(
                    ErrorCode.INVALID_FIELD_VALUE, "size", "10, 20, 50 중 하나만 허용합니다.");
        }
        return new PageRequest(number, size);
    }

    private int integer(String raw, String field, int defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw new BusinessException(
                    ErrorCode.INVALID_FIELD_VALUE, field, "정수 값만 허용합니다.");
        }
    }

    private record PageRequest(int number, int size) {
    }
}
