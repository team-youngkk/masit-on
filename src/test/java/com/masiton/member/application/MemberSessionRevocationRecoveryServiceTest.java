package com.masiton.member.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.masiton.member.application.port.out.MemberSessionRevocationRecoveryQueue;
import com.masiton.member.application.port.out.MemberSessionRevocationRecoveryJobStore;
import com.masiton.member.application.port.out.MemberSessionRevocationStore;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemberSessionRevocationRecoveryService")
class MemberSessionRevocationRecoveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:10:00Z");

    @Mock
    private MemberSessionRevocationRecoveryQueue recoveryQueue;
    @Mock
    private MemberSessionRevocationRecoveryJobStore recoveryJobs;
    @Mock
    private MemberSessionRevocationStore revocations;

    @Test
    @DisplayName("15분 재시도에서 하나가 실패해도 나머지 폐기 기록을 계속 처리한다")
    void run_복구기록중하나실패_나머지계속처리() {
        MemberSessionRevocation failed = revocation();
        MemberSessionRevocation succeeding = revocation();
        given(recoveryJobs.claimDue(NOW, 50)).willReturn(List.of(failed, succeeding));
        given(recoveryQueue.claimDue(NOW, 50)).willReturn(List.of());
        given(recoveryJobs.findUnresolvedBefore(NOW.minusSeconds(60 * 60), NOW, 10)).willReturn(List.of());
        doThrow(new IllegalStateException("database unavailable")).when(revocations).record(failed);
        MemberSessionRevocationRecoveryService service = new MemberSessionRevocationRecoveryService(
                recoveryQueue, recoveryJobs, revocations, Clock.fixed(NOW, ZoneOffset.UTC));

        service.run();

        verify(revocations).record(failed);
        verify(revocations).record(succeeding);
        verify(recoveryJobs).reschedule(failed.sessionId(), NOW);
        verify(recoveryJobs).complete(succeeding.sessionId());
    }

    @Test
    @DisplayName("한 시간 넘게 미해결인 복구 작업은 ERROR로 알린다")
    void run_한시간미해결복구작업_ERROR알림() {
        UUID sessionId = UUID.randomUUID();
        given(recoveryQueue.claimDue(NOW, 50)).willReturn(List.of());
        given(recoveryJobs.claimDue(NOW, 50)).willReturn(List.of());
        given(recoveryJobs.findUnresolvedBefore(NOW.minusSeconds(60 * 60), NOW, 10)).willReturn(List.of(sessionId));
        MemberSessionRevocationRecoveryService service = new MemberSessionRevocationRecoveryService(
                recoveryQueue, recoveryJobs, revocations, Clock.fixed(NOW, ZoneOffset.UTC));
        Logger logger = (Logger) LoggerFactory.getLogger(MemberSessionRevocationRecoveryService.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);

        try {
            service.run();
        } finally {
            logger.detachAppender(events);
        }

        org.assertj.core.api.Assertions.assertThat(events.list)
                .anySatisfy(event -> {
                    org.assertj.core.api.Assertions.assertThat(event.getLevel().toString()).isEqualTo("ERROR");
                    org.assertj.core.api.Assertions.assertThat(event.getFormattedMessage()).contains(sessionId.toString());
                });
    }

    @Test
    @DisplayName("Redis 브리지 폐기는 PostgreSQL 복구 작업으로 먼저 넘긴다")
    void run_Redis브리지_내구복구작업선기록후제거() {
        MemberSessionRevocation revocation = revocation();
        given(recoveryQueue.claimDue(NOW, 50)).willReturn(List.of(revocation));
        given(recoveryJobs.claimDue(NOW, 50)).willReturn(List.of());
        given(recoveryJobs.findUnresolvedBefore(NOW.minusSeconds(60 * 60), NOW, 10)).willReturn(List.of());
        MemberSessionRevocationRecoveryService service = new MemberSessionRevocationRecoveryService(
                recoveryQueue, recoveryJobs, revocations, Clock.fixed(NOW, ZoneOffset.UTC));

        service.run();

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(revocations, recoveryQueue);
        order.verify(revocations).record(revocation);
        order.verify(recoveryQueue).complete(revocation);
        verify(recoveryJobs, org.mockito.Mockito.never()).enqueue(revocation, NOW);
    }

    @Test
    @DisplayName("Redis 브리지 marker 실패는 작업을 보상 기록하고 브리지를 보존한다")
    void run_Redis브리지_marker실패_보상기록후브리지보존() {
        MemberSessionRevocation revocation = revocation();
        given(recoveryQueue.claimDue(NOW, 50)).willReturn(List.of(revocation));
        given(recoveryJobs.claimDue(NOW, 50)).willReturn(List.of());
        given(recoveryJobs.findUnresolvedBefore(NOW.minusSeconds(60 * 60), NOW, 10)).willReturn(List.of());
        doThrow(new IllegalStateException("database unavailable")).when(revocations).record(revocation);
        MemberSessionRevocationRecoveryService service = new MemberSessionRevocationRecoveryService(
                recoveryQueue, recoveryJobs, revocations, Clock.fixed(NOW, ZoneOffset.UTC));

        service.run();

        verify(recoveryJobs).enqueue(revocation, NOW);
        verify(recoveryQueue, org.mockito.Mockito.never()).complete(revocation);
    }

    private MemberSessionRevocation revocation() {
        return new MemberSessionRevocation(UUID.randomUUID(), NOW, NOW.plusSeconds(60));
    }
}
