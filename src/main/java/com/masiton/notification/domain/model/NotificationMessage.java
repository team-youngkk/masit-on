package com.masiton.notification.domain.model;

/** title은 100자, message는 500자 이내이며 빈 값이 아니어야 한다(DB CHECK). */
public record NotificationMessage(String title, String message) {
}
