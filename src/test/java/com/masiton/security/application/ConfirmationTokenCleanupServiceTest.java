package com.masiton.security.application;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.security.application.port.out.ConfirmationTokenRepositoryPort;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("확인 토큰 보관 기한 정리 서비스")
class ConfirmationTokenCleanupServiceTest {

    @Test
    @DisplayName("발급 시점 기준 24시간이 지난 레코드를 최대 100건 정리한다")
    void deleteExpiredRetentionRecords_보관기한지난레코드_제한건수로정리한다() {
        ConfirmationTokenRepositoryPort confirmationTokenRepository = mock(ConfirmationTokenRepositoryPort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-28T01:00:00Z"), ZoneOffset.UTC);
        ConfirmationTokenCleanupService service = new ConfirmationTokenCleanupService(confirmationTokenRepository, clock);

        service.deleteExpiredRetentionRecords();

        verify(confirmationTokenRepository).deleteExpiredRetentionRecords(
                eq(OffsetDateTime.parse("2026-07-27T01:00:00Z")), eq(100));
    }
}
