package com.masiton.member.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.masiton.member.application.MemberActionMailOutboxDelivery;
import com.masiton.member.domain.model.MemberActionMailOutbox;

public interface MemberActionMailOutboxStore {

    void enqueue(MemberActionMailOutbox outbox, Instant now);

    void cancelIneligible(Instant now);

    List<MemberActionMailOutboxDelivery> claimDue(Instant now, int limit);

    boolean confirmDelivery(UUID outboxId, Instant now);

    void markSent(UUID outboxId, Instant sentAt);

    void reschedule(UUID outboxId, Instant now);
}
