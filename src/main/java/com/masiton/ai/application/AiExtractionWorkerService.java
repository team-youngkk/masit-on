package com.masiton.ai.application;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
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
import org.springframework.beans.factory.annotation.Autowired;

import com.masiton.ai.application.port.out.AiExtractionWorkerStore;
import com.masiton.ai.application.port.out.AiRegistrationUnitRetryStore;
import com.masiton.ai.application.port.out.AiExtractionWorkerPolicy;
import com.masiton.ai.application.port.out.AiExtractionWorkerStore.ClaimedJob;
import com.masiton.ai.application.port.out.AiProviderException;
import com.masiton.ai.application.port.out.AiExtractionResultProcessor;
import com.masiton.ai.application.port.out.AiVideoExtractionProvider;
import com.masiton.ai.application.port.out.TemporaryInputCipher;
import com.masiton.ai.application.port.out.TemporaryInputDecryptionException;
import com.masiton.ai.application.port.out.dto.AiVideoExtractionRequest;
import com.masiton.ai.application.port.out.dto.AiVideoExtractionResult;
import tools.jackson.databind.ObjectMapper;

@Service
public class AiExtractionWorkerService {

    private static final Logger log = LoggerFactory.getLogger(AiExtractionWorkerService.class);
    private final AiExtractionWorkerStore store;
    private final AiRegistrationUnitRetryStore retryStore;
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

    @Autowired
    public AiExtractionWorkerService(AiExtractionWorkerStore store,
                                     AiRegistrationUnitRetryStore retryStore,
                                     AiVideoExtractionProvider provider,
                                     Optional<AiExtractionResultProcessor> resultProcessor,
                                     TemporaryInputCipher temporaryInputCipher,
                                     AiExtractionWorkerPolicy properties,
                                     Clock aiWorkerClock,
                                     AiWorkerDelay delay) {
        this.store = store;
        this.retryStore = retryStore;
        this.provider = provider;
        this.resultProcessor = resultProcessor;
        this.temporaryInputCipher = temporaryInputCipher;
        this.properties = properties;
        this.clock = aiWorkerClock;
        this.delay = delay;
    }

    public AiExtractionWorkerService(AiExtractionWorkerStore store, AiVideoExtractionProvider provider,
                                     Optional<AiExtractionResultProcessor> resultProcessor,
                                     TemporaryInputCipher temporaryInputCipher,
                                     AiExtractionWorkerPolicy properties, Clock aiWorkerClock, AiWorkerDelay delay) {
        this(store, null, provider, resultProcessor, temporaryInputCipher, properties, aiWorkerClock, delay);
    }

    public void poll() {
        if (!properties.isEnabled() || resultProcessor.isEmpty()
                || !acceptingClaims.get() || !inFlight.compareAndSet(false, true)) {
            return;
        }
        try {
            OffsetDateTime now = now();
            Optional<ClaimedJob> recovery = store.claimRecovery(workerId, now,
                    now.plus(properties.getLeaseDuration()));
            if (recovery.isPresent()) {
                execute(recovery.get());
                return;
            }
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
            log.warn("AI extraction worker polling failed: category=INFRASTRUCTURE, exceptionType={}, stackTrace={}",
                    exception.getClass().getName(), sanitizedStackTrace(exception));
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
                    Optional<Boolean> resumed = resultProcessor.orElseThrow().resumePending(job.jobId(), workerId,
                            attemptNo, attemptStartedAt, now(),
                            (jobId, retryId, supplementText) -> extractSupplement(job, retryId, supplementText));
                    if (resumed.isPresent()) {
                        if (!resumed.get()) {
                            log.warn("AI extraction remediation recovery discarded after lease ownership loss: jobId={}",
                                    job.jobId());
                        }
                        return;
                    }
                    String supplement = job.temporaryInput() == null
                            ? "" : temporaryInputCipher.decrypt(job.temporaryInput());
                    AiVideoExtractionResult result = provider.extract(
                            new AiVideoExtractionRequest(job.videoUrl(), supplement));
                    OffsetDateTime finishedAt = now();
                    if (!resultProcessor.orElseThrow().process(job.jobId(), workerId, attemptNo,
                            attemptStartedAt, finishedAt, result,
                            (jobId, retryId, supplementText) -> extractSupplement(job, retryId, supplementText))) {
                        log.warn("AI extraction result discarded after lease ownership loss: jobId={}", job.jobId());
                    }
                    return;
                } catch (AiProviderException exception) {
                    OffsetDateTime finishedAt = now();
                    if (!exception.retryable() || attemptNo >= properties.getMaxAttempts()) {
                        store.completeFailure(job.jobId(), workerId, attemptNo, attemptStartedAt,
                                finishedAt, exception.category().name(), exception.httpStatus());
                        return;
                    }
                    if (!store.recordRetryableFailure(job.jobId(), workerId, attemptNo, attemptStartedAt,
                            finishedAt, exception.category().name(), exception.httpStatus())) {
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
                        log.warn("AI extraction temporary input key unavailable; lease recovery will retry: jobId={}, "
                                        + "category=KEY_UNAVAILABLE, exceptionType={}, stackTrace={}",
                                job.jobId(), exception.getClass().getName(), sanitizedStackTrace(exception));
                        return;
                    }
                    store.completeFailure(job.jobId(), workerId, attemptNo, attemptStartedAt, now(), "INPUT");
                    return;
                } catch (RuntimeException exception) {
                    log.warn("AI extraction execution failed unexpectedly; lease recovery will retry: jobId={}, "
                                    + "category=INFRASTRUCTURE, exceptionType={}, stackTrace={}",
                            job.jobId(), exception.getClass().getName(), sanitizedStackTrace(exception));
                    return;
                }
            }
        } finally {
            heartbeat.cancel(false);
        }
    }

    private Optional<AiExtractionResultProcessor.SupplementResult> extractSupplement(
            ClaimedJob job, UUID retryId, String supplementText) {
        int attemptNo = job.attemptNo();
        OffsetDateTime lastStartedAt = null;
        OffsetDateTime lastFinishedAt = null;
        while (acceptingClaims.get()) {
            OffsetDateTime reservedAt = now();
            Optional<Integer> reserved = retryId == null
                    ? store.reserveSupplementProviderCall(job.jobId(), workerId, reservedAt,
                            reservedAt.plus(properties.getLeaseDuration()), reservedAt.minus(properties.getQuotaWindow()),
                            properties.getApplicationQuotaLimit())
                    : store.reserveSupplementProviderCall(job.jobId(), workerId, retryId, reservedAt,
                            reservedAt.plus(properties.getLeaseDuration()), reservedAt.minus(properties.getQuotaWindow()),
                            properties.getApplicationQuotaLimit());
            if (reserved.isEmpty()) {
                return attemptNo == job.attemptNo() ? Optional.empty()
                        : Optional.of(failedSupplement(attemptNo, lastStartedAt, lastFinishedAt));
            }
            attemptNo = reserved.get();
            OffsetDateTime startedAt = now();
            lastStartedAt = startedAt;
            try {
                AiVideoExtractionResult result = provider.extract(new AiVideoExtractionRequest(job.videoUrl(), supplementText));
                if (retryId != null && retryStore != null) {
                    ObjectMapper mapper = new ObjectMapper();
                    var payload = mapper.createObjectNode();
                    payload.set("candidates", result.candidates());
                    payload.put("providerRequestId", result.providerRequestId());
                    String serialized = mapper.writeValueAsString(payload);
                    TemporaryInputCipher.EncryptedInput encrypted = temporaryInputCipher.encrypt(serialized);
                    if (!retryStore.storeProviderResult(retryId, job.jobId(), workerId, attemptNo,
                            result.providerRequestId(), startedAt, encrypted.ciphertext(), encrypted.keyId(), now())) {
                        return Optional.empty();
                    }
                } else if (!store.recordSuccessfulSupplementAttempt(job.jobId(), workerId, attemptNo, startedAt, now(),
                        result.providerRequestId())) {
                    return Optional.empty();
                }
                return Optional.of(new AiExtractionResultProcessor.SupplementResult(
                        attemptNo, startedAt, now(), result, true));
            } catch (AiProviderException exception) {
                OffsetDateTime finishedAt = now();
                lastFinishedAt = finishedAt;
                if (!exception.retryable() || attemptNo >= properties.getMaxAttempts() || attemptNo >= 4) {
                    store.recordRetryableFailure(job.jobId(), workerId, attemptNo, startedAt, finishedAt,
                            exception.category().name(), exception.httpStatus());
                    return Optional.of(new AiExtractionResultProcessor.SupplementResult(attemptNo, startedAt,
                            finishedAt, new AiVideoExtractionResult(new tools.jackson.databind.ObjectMapper().createObjectNode(), null), false));
                }
                if (!store.recordRetryableFailure(job.jobId(), workerId, attemptNo, startedAt, finishedAt,
                        exception.category().name(), exception.httpStatus())) {
                    return Optional.empty();
                }
                Duration backoff = attemptNo == 1 ? properties.getFirstBackoff() : properties.getSecondBackoff();
                if (!delay.await(backoff) || !acceptingClaims.get()) {
                    return Optional.of(new AiExtractionResultProcessor.SupplementResult(attemptNo, startedAt,
                            finishedAt, new AiVideoExtractionResult(new tools.jackson.databind.ObjectMapper().createObjectNode(), null), false));
                }
            }
        }
        return attemptNo == job.attemptNo() ? Optional.empty()
                : Optional.of(failedSupplement(attemptNo, lastStartedAt, lastFinishedAt));
    }

    private AiExtractionResultProcessor.SupplementResult failedSupplement(
            int attemptNo, OffsetDateTime startedAt, OffsetDateTime finishedAt) {
        return new AiExtractionResultProcessor.SupplementResult(attemptNo, startedAt, finishedAt,
                new AiVideoExtractionResult(new tools.jackson.databind.ObjectMapper().createObjectNode(), null), false);
    }

    void heartbeat(UUID jobId) {
        try {
            OffsetDateTime now = now();
            if (!store.heartbeat(jobId, workerId, now, now.plus(properties.getLeaseDuration()))) {
                log.warn("AI extraction heartbeat lost lease ownership: jobId={}", jobId);
            }
        } catch (RuntimeException exception) {
            log.warn("AI extraction heartbeat failed: jobId={}, category=INFRASTRUCTURE, exceptionType={}, stackTrace={}",
                    jobId, exception.getClass().getName(), sanitizedStackTrace(exception));
        }
    }

    private static String sanitizedStackTrace(RuntimeException exception) {
        return Arrays.stream(exception.getStackTrace())
                .map(StackTraceElement::toString)
                .reduce((first, second) -> first + " <- " + second)
                .orElse("empty");
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
