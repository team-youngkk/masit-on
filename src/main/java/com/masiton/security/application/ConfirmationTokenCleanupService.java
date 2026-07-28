package com.masiton.security.application;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.security.application.port.out.ConfirmationTokenRepositoryPort;

@Service
class ConfirmationTokenCleanupService {

    private static final Duration RETENTION_PERIOD = Duration.ofHours(24);
    private static final int CLEANUP_LIMIT = 100;

    private final ConfirmationTokenRepositoryPort confirmationTokenRepository;
    private final Clock clock;

    @Autowired
    ConfirmationTokenCleanupService(ConfirmationTokenRepositoryPort confirmationTokenRepository) {
        this(confirmationTokenRepository, Clock.systemUTC());
    }

    ConfirmationTokenCleanupService(ConfirmationTokenRepositoryPort confirmationTokenRepository, Clock clock) {
        this.confirmationTokenRepository = confirmationTokenRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteExpiredRetentionRecords() {
        confirmationTokenRepository.deleteExpiredRetentionRecords(
                OffsetDateTime.now(clock).minus(RETENTION_PERIOD), CLEANUP_LIMIT);
    }
}
