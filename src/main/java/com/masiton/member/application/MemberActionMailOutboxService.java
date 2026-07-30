package com.masiton.member.application;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.masiton.member.application.port.out.MemberActionMailOutboxStore;
import com.masiton.member.application.port.out.MemberActionTokenCipher;

@Service
public class MemberActionMailOutboxService {

    private static final Logger log = LoggerFactory.getLogger(MemberActionMailOutboxService.class);
    private static final int BATCH_SIZE = 50;

    private final MemberActionMailOutboxStore outbox;
    private final MemberActionTokenCipher tokenCipher;
    private final com.masiton.member.application.port.out.MemberActionTokenDeliveryPort delivery;
    private final Clock clock;

    public MemberActionMailOutboxService(MemberActionMailOutboxStore outbox, MemberActionTokenCipher tokenCipher,
            com.masiton.member.application.port.out.MemberActionTokenDeliveryPort delivery, Clock memberSessionClock) {
        this.outbox = outbox;
        this.tokenCipher = tokenCipher;
        this.delivery = delivery;
        this.clock = memberSessionClock;
    }

    @Scheduled(
            fixedDelayString = "${masiton.member.action-mail.dispatch-interval:PT1M}",
            initialDelayString = "${masiton.member.action-mail.initial-dispatch-delay:PT1M}"
    )
    public void run() {
        Instant now = Instant.now(clock);
        try {
            outbox.cancelIneligible(now);
            for (MemberActionMailOutboxDelivery work : outbox.claimDue(now, BATCH_SIZE)) {
                dispatch(work);
            }
        } catch (RuntimeException exception) {
            log.warn("member action-mail outbox dispatch lookup failed");
        }
    }

    private void dispatch(MemberActionMailOutboxDelivery work) {
        Instant now = Instant.now(clock);
        try {
            if (!outbox.confirmDelivery(work.outbox().id(), now)) {
                return;
            }
            String rawToken = tokenCipher.decrypt(work.outbox());
            delivery.send(work.recipientEmail(), work.outbox().purpose(), rawToken);
            outbox.markSent(work.outbox().id(), now);
        } catch (RuntimeException exception) {
            try {
                outbox.reschedule(work.outbox().id(), now);
            } catch (RuntimeException rescheduleFailure) {
                exception.addSuppressed(rescheduleFailure);
            }
            log.warn("member action-mail outbox dispatch failed: outboxId={}, purpose={}",
                    work.outbox().id(), work.outbox().purpose());
        }
    }
}
