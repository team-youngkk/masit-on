package com.masiton.ai.application;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.masiton.ai.application.port.out.AiExtractionWorkerStore;
import com.masiton.ai.application.port.out.AiExtractionWorkerPolicy;
import com.masiton.ai.application.port.out.AiExtractionWorkerStore.ClaimedJob;
import com.masiton.ai.application.port.out.AiProviderException;
import com.masiton.ai.application.port.out.AiExtractionResultProcessor;
import com.masiton.ai.application.port.out.AiVideoExtractionProvider;
import com.masiton.ai.application.port.out.TemporaryInputCipher;
import com.masiton.ai.application.port.out.TemporaryInputDecryptionException;
import com.masiton.ai.application.port.out.dto.AiVideoExtractionRequest;
import com.masiton.ai.application.port.out.dto.AiVideoExtractionResult;

@Service
public class AiExtractionWorkerService {

    private static final Logger log = LoggerFactory.getLogger(AiExtractionWorkerService.class);
    private final AiExtractionWorkerStore store;
    private final AiVideoExtractionProvider provider;
    private final Optional<AiExtractionResultProcessor> resultProcessor;
    private final TemporaryInputCipher temporaryInputCipher;
    private final AiExtractionWorkerPolicy properties;
    private final Clock clock;
    private final AiWorkerDelay delay;
    private final String workerId = "ai-worker-" + UUID.randomUUID();
    private final AtomicBoolean acceptingClaims = new AtomicBoolean(true);
    private final AtomicBoolean inFlight = new AtomicBoolean(false);
    private final AtomicBoolean quotaWarningLogged = new AtomicBoolean(false);
    private final AtomicBoolean quotaHardStopLogged = new AtomicBoolean(false);
    private final Object drainMonitor = new Object();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ai-extraction-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public AiExtractionWorkerService(AiExtractionWorkerStore store,
                                     AiVideoExtractionProvider provider,
                                     Optional<AiExtractionResultProcessor> resultProcessor,
                                     TemporaryInputCipher temporaryInputCipher,
                                     AiExtractionWorkerPolicy properties,
                                     Clock aiWorkerClock,
                                     AiWorkerDelay delay) {
        this.store = store;
        this.provider = provider;
        this.resultProcessor = resultProcessor;
        this.temporaryInputCipher = temporaryInputCipher;
        this.properties = properties;
        this.clock = aiWorkerClock;
        this.delay = delay;
    }

    public void poll() {
        if (!properties.isEnabled() || resultProcessor.isEmpty()
                || !acceptingClaims.get() || !inFlight.compareAndSet(false, true)) {
            return;
        }
        try {
            OffsetDateTime now = now();
            store.failExpiredExhausted(now, properties.getMaxAttempts());
            OffsetDateTime quotaWindowStart = now.minus(properties.getQuotaWindow());
            long quotaUsage = store.quotaUsage(quotaWindowStart);
            if (quotaUsage >= properties.getApplicationQuotaLimit()) {
                int failedJobs = store.failQueuedForQuota(now);
                quotaWarningLogged.set(false);
                if (quotaHardStopLogged.compareAndSet(false, true)) {
                    log.warn("AI extraction quota hard stop entered: usage={}, limit={}, failedQueuedJobs={}",
                            quotaUsage, properties.getApplicationQuotaLimit(), failedJobs);
                }
                return;
            }
            quotaHardStopLogged.set(false);
            if (quotaUsage * 100 >= properties.getApplicationQuotaLimit() * properties.getQuotaWarningPercent()) {
                if (quotaWarningLogged.compareAndSet(false, true)) {
                    log.warn("AI extraction quota warning entered: usage={}, limit={}",
                            quotaUsage, properties.getApplicationQuotaLimit());
                }
            } else {
                quotaWarningLogged.set(false);
            }
            store.claim(workerId, now, now.plus(properties.getLeaseDuration()), properties.getMaxAttempts(),
                            quotaWindowStart, properties.getApplicationQuotaLimit())
                    .ifPresent(this::execute);
        } catch (RuntimeException exception) {
            log.warn("AI extraction worker polling failed: category=INFRASTRUCTURE");
        } finally {
            inFlight.set(false);
            synchronized (drainMonitor) {
                drainMonitor.notifyAll();
            }
        }
    }

    private void execute(ClaimedJob job) {
        ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(
                () -> heartbeat(job.jobId()), properties.getHeartbeatInterval().toMillis(),
                properties.getHeartbeatInterval().toMillis(), TimeUnit.MILLISECONDS);
        try {
            int attemptNo = job.attemptNo();
            while (acceptingClaims.get()) {
                OffsetDateTime attemptStartedAt = now();
                try {
                    String supplement = job.temporaryInput() == null
                            ? "" : temporaryInputCipher.decrypt(job.temporaryInput());
                    AiVideoExtractionResult result = provider.extract(
                            new AiVideoExtractionRequest(job.videoUrl(), supplement));
                    OffsetDateTime finishedAt = now();
                    if (!resultProcessor.orElseThrow().process(job.jobId(), workerId, attemptNo,
                            attemptStartedAt, finishedAt, result)) {
                        log.warn("AI extraction result discarded after lease ownership loss: jobId={}", job.jobId());
                    }
                    return;
                } catch (AiProviderException exception) {
                    OffsetDateTime finishedAt = now();
                    if (!exception.retryable() || attemptNo >= properties.getMaxAttempts()) {
                        store.completeFailure(job.jobId(), workerId, attemptNo, attemptStartedAt,
                                finishedAt, exception.category().name());
                        return;
                    }
                    if (!store.recordRetryableFailure(job.jobId(), workerId, attemptNo, attemptStartedAt,
                            finishedAt, exception.category().name())) {
                        return;
                    }
                    Duration backoff = attemptNo == 1
                            ? properties.getFirstBackoff() : properties.getSecondBackoff();
                    if (!delay.await(backoff) || !acceptingClaims.get()) {
                        return;
                    }
                    OffsetDateTime retryAt = now();
                    Optional<Integer> nextAttempt = store.beginRetry(job.jobId(), workerId, retryAt,
                            retryAt.plus(properties.getLeaseDuration()), properties.getMaxAttempts(),
                            retryAt.minus(properties.getQuotaWindow()), properties.getApplicationQuotaLimit());
                    if (nextAttempt.isEmpty()) {
                        if (store.quotaUsage(retryAt.minus(properties.getQuotaWindow()))
                                >= properties.getApplicationQuotaLimit()) {
                            store.failWithoutAttempt(job.jobId(), workerId, retryAt, "QUOTA_HARD_STOP");
                        }
                        return;
                    }
                    attemptNo = nextAttempt.get();
                } catch (TemporaryInputDecryptionException exception) {
                    if (exception.retryable()) {
                        log.warn("AI extraction temporary input key unavailable; lease recovery will retry: jobId={}, category=KEY_UNAVAILABLE",
                                job.jobId());
                        return;
                    }
                    store.completeFailure(job.jobId(), workerId, attemptNo, attemptStartedAt, now(), "INPUT");
                    return;
                } catch (RuntimeException exception) {
                    log.warn("AI extraction execution failed unexpectedly; lease recovery will retry: jobId={}, category=INFRASTRUCTURE",
                            job.jobId());
                    return;
                }
            }
        } finally {
            heartbeat.cancel(false);
        }
    }

    private void heartbeat(UUID jobId) {
        try {
            OffsetDateTime now = now();
            if (!store.heartbeat(jobId, workerId, now, now.plus(properties.getLeaseDuration()))) {
                log.warn("AI extraction heartbeat lost lease ownership: jobId={}", jobId);
            }
        } catch (RuntimeException exception) {
            log.warn("AI extraction heartbeat failed: jobId={}, category=INFRASTRUCTURE", jobId);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    @PreDestroy
    void shutdown() {
        acceptingClaims.set(false);
        long deadline = System.nanoTime() + properties.getDrainTimeout().toNanos();
        synchronized (drainMonitor) {
            while (inFlight.get()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) break;
                try {
                    TimeUnit.NANOSECONDS.timedWait(drainMonitor, remaining);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        heartbeatExecutor.shutdownNow();
    }
}
