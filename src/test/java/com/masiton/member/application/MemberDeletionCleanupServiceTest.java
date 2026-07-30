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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.masiton.member.application.port.out.MemberAccountRepository;
import com.masiton.member.application.port.out.MemberActionTokenRepository;
import com.masiton.member.application.port.out.MemberDeletionJobStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.slf4j.LoggerFactory.getLogger;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemberDeletionCleanupService")
class MemberDeletionCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:10:00Z");

    @Mock private MemberDeletionJobStore jobs;
    @Mock private MemberAccountRepository accounts;
    @Mock private MemberActionTokenRepository actionTokens;

    @Test
    @DisplayName("한 시간 넘게 실패한 탈퇴 정리 작업은 운영 알림 로그를 남긴다")
    void run_한시간초과정리실패_운영알림을남긴다() {
        UUID memberId = UUID.randomUUID();
        given(jobs.claimDue(NOW, 50)).willReturn(List.of(memberId));
        doThrow(new IllegalStateException("database unavailable")).when(actionTokens).deleteByMemberId(memberId);
        given(jobs.hasExceededOneHour(memberId, NOW)).willReturn(true);
        MemberDeletionCleanupService service = new MemberDeletionCleanupService(
                jobs, accounts, actionTokens, Clock.fixed(NOW, ZoneOffset.UTC));
        Logger logger = (Logger) getLogger(MemberDeletionCleanupService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            service.run();
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel().toString()).isEqualTo("ERROR");
            assertThat(event.getFormattedMessage()).contains("requires operations intervention");
        });
        verify(jobs).reschedule(memberId, NOW);
    }
}
