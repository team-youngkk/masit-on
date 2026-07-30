package com.masiton.member.application;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.masiton.member.application.port.out.MemberSessionRevocationRecoveryQueue;
import com.masiton.member.application.port.out.MemberSessionRevocationRecoveryJobStore;
import com.masiton.member.application.port.out.MemberSessionRevocationStore;

@Service
public class MemberSessionRevocationRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(MemberSessionRevocationRecoveryService.class);
    private static final int BATCH_SIZE = 50;

    private static final int ALERT_BATCH_SIZE = 10;
    private final MemberSessionRevocationRecoveryQueue redisBridge;
    private final MemberSessionRevocationRecoveryJobStore recoveryJobs;
    private final MemberSessionRevocationStore revocations;
    private final Clock clock;

    public MemberSessionRevocationRecoveryService(MemberSessionRevocationRecoveryQueue redisBridge,
            MemberSessionRevocationRecoveryJobStore recoveryJobs, MemberSessionRevocationStore revocations,
            Clock memberSessionClock) {
        this.redisBridge = redisBridge;
        this.recoveryJobs = recoveryJobs;
        this.revocations = revocations;
        this.clock = memberSessionClock;
    }

    @Scheduled(fixedDelayString = "PT15M")
    public void run() {
        Instant now = Instant.now(clock);
        recoverRedisBridge(now);
        recoverPostgreSqlJobs(now);
        alertUnresolvedJobs(now);
    }

    private void recoverRedisBridge(Instant now) {
        for (MemberSessionRevocation revocation : redisBridge.claimDue(now, BATCH_SIZE)) {
            try {
                revocations.record(revocation);
                redisBridge.complete(revocation);
            } catch (RuntimeException exception) {
                enqueueRecoveryBestEffort(revocation, now, exception);
                log.warn("member session revocation Redis bridge recovery failed: sessionId={}", revocation.sessionId());
            }
        }
    }

    private void enqueueRecoveryBestEffort(MemberSessionRevocation revocation, Instant now, RuntimeException originalFailure) {
        try {
            recoveryJobs.enqueue(revocation, now);
        } catch (RuntimeException recoveryFailure) {
            originalFailure.addSuppressed(recoveryFailure);
            log.warn("member session revocation recovery job enqueue failed: sessionId={}", revocation.sessionId());
        }
    }

    private void recoverPostgreSqlJobs(Instant now) {
        try {
            for (MemberSessionRevocation revocation : recoveryJobs.claimDue(now, BATCH_SIZE)) {
                try {
                    revocations.record(revocation);
                    recoveryJobs.complete(revocation.sessionId());
                } catch (RuntimeException exception) {
                    recoveryJobs.reschedule(revocation.sessionId(), now);
                    log.warn("member session revocation recovery job failed: sessionId={}", revocation.sessionId());
                }
            }
        } catch (RuntimeException exception) {
            log.warn("member session revocation recovery job lookup failed");
        }
    }

    private void alertUnresolvedJobs(Instant now) {
        try {
            recoveryJobs.findUnresolvedBefore(now.minus(1, java.time.temporal.ChronoUnit.HOURS), now, ALERT_BATCH_SIZE)
                    .forEach(sessionId -> log.error(
                            "member session revocation recovery is unresolved for at least one hour: sessionId={}", sessionId));
        } catch (RuntimeException exception) {
            log.warn("member session revocation recovery alert lookup failed");
        }
    }
}
