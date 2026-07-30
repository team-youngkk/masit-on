package com.masiton.member.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.masiton.member.application.port.out.MemberDeletionJobStore;

@Service
public class MemberDeletionCleanupService {
    private static final Logger log = LoggerFactory.getLogger(MemberDeletionCleanupService.class);
    private final MemberDeletionJobStore jobs;
    private final MemberDeletionCleanupCommandService cleanupCommands;
    private final Clock clock;

    public MemberDeletionCleanupService(MemberDeletionJobStore jobs, MemberDeletionCleanupCommandService cleanupCommands,
            Clock memberSessionClock) {
        this.jobs = jobs;
        this.cleanupCommands = cleanupCommands;
        this.clock = memberSessionClock;
    }

    @Scheduled(fixedDelayString = "PT15M")
    public void run() {
        Instant now = Instant.now(clock);
        for (UUID memberId : jobs.claimDue(now, 50)) {
            try {
                cleanupCommands.cleanup(memberId);
            } catch (RuntimeException exception) {
                log.warn("member deletion cleanup failed: memberId={}", memberId);
                if (jobs.hasExceededOneHour(memberId, now)) {
                    log.error("member deletion cleanup requires operations intervention: memberId={}", memberId);
                }
                jobs.reschedule(memberId, now);
            }
        }
    }

}
