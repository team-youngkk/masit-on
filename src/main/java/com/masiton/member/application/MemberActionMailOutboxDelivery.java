package com.masiton.member.application;

import com.masiton.member.domain.model.MemberActionMailOutbox;

public record MemberActionMailOutboxDelivery(MemberActionMailOutbox outbox, String recipientEmail) {
}
