package com.masiton.ai.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.masiton.ai.application.port.in.YoutubeChannelBackfillUseCase;
import com.masiton.ai.application.port.in.AiExtractionJobUseCase;
import com.masiton.ai.application.port.out.YoutubeChannelBackfillRunStore;
import com.masiton.ai.application.port.out.YoutubeChannelBackfillRunStore.ClaimedRun;
import com.masiton.ai.application.port.out.YoutubeChannelBackfillRunStore.Run;
import com.masiton.ai.application.port.out.YoutubeChannelVideoQueryPort;
import com.masiton.ai.application.port.out.dto.AiExtractionJobView;
import com.masiton.ai.application.port.out.YoutubeChannelBackfillPolicy;
import com.masiton.ai.application.port.out.YoutubeChannelBackfillMetrics;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;

@Service
public class YoutubeChannelBackfillService implements YoutubeChannelBackfillUseCase {

    private final YoutubeChannelBackfillRunStore runs;
    private final YoutubeChannelVideoQueryPort videos;
    private final AiExtractionJobUseCase jobs;
    private final YoutubeChannelBackfillPolicy properties;
    private final Clock clock;
    private final YoutubeChannelBackfillMetrics metrics;
    private final AtomicBoolean polling = new AtomicBoolean();
    private final String workerId = "youtube-backfill-" + UUID.randomUUID();

    public YoutubeChannelBackfillService(YoutubeChannelBackfillRunStore runs, YoutubeChannelVideoQueryPort videos,
                                         AiExtractionJobUseCase jobs, YoutubeChannelBackfillPolicy properties,
                                         Clock aiWorkerClock, YoutubeChannelBackfillMetrics metrics) {
        this.runs = runs;
        this.videos = videos;
        this.jobs = jobs;
        this.properties = properties;
        this.clock = aiWorkerClock;
        this.metrics = metrics;
    }

    @Override
    public StartResult start(UUID creatorId) {
        if (creatorId == null) throw new BusinessException(ErrorCode.INVALID_IDENTIFIER);
        if (!properties.isEnabled()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AIEXTRACT_YOUTUBE_BACKFILL_DISABLED", "YouTube backfill is disabled.");
        }
        // The INSERT ... SELECT in the store makes this eligibility check and active-run reuse atomic.
        YoutubeChannelBackfillRunStore.StartRun start = runs.createOrReuse(creatorId, now())
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT,
                        "AIEXTRACT_YOUTUBE_BACKFILL_WATCH_NOT_ACTIVE",
                        "An active verified channel watch is required."));
        metrics.recordRunStart(start.reused());
        return new StartResult(start.run().runId(), start.run().status(), start.reused());
    }

    @Override
    public RunStatus get(UUID creatorId, UUID runId) {
        Run run = runs.find(creatorId, runId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return new RunStatus(run.runId(), run.status(), run.scannedCount(), run.submittedCount(), run.reusedCount(),
                run.lastErrorCategory(), run.createdAt(), run.updatedAt());
    }

    @Override
    public void stop(UUID creatorId, UUID runId) {
        if (creatorId == null || runId == null) {
            throw new BusinessException(ErrorCode.INVALID_IDENTIFIER);
        }
        runs.stop(creatorId, runId, now());
    }

    @Override
    public void poll() {
        if (!properties.isEnabled() || !polling.compareAndSet(false, true)) return;
        try {
            runs.stopRunsForInactiveWatches(now());
            Optional<ClaimedRun> claimed = runs.claim(now(), now().plus(properties.getLeaseDuration()), workerId);
            claimed.ifPresent(this::processPage); // no transaction encloses this external call
        } finally {
            polling.set(false);
        }
    }

    private void processPage(ClaimedRun run) {
        try {
            if (run.pageCount() >= properties.getMaxPagesPerRun()
                    || run.scannedCount() >= properties.getMaxVideosPerRun()) {
                runs.stop(run.creatorId(), run.runId(), now());
                return;
            }
            if (!runs.isClaimActive(run.runId(), run.leaseOwner(), now())) return;
            YoutubeChannelVideoQueryPort.VideoPage page = videos.query(run.channelId(), run.pageToken());
            if (!runs.isClaimActive(run.runId(), run.leaseOwner(), now())) return;
            if (run.scannedCount() + page.videoIds().size() > properties.getMaxVideosPerRun()) {
                runs.stop(run.creatorId(), run.runId(), now());
                return;
            }
            int submitted = 0;
            int reused = 0;
            for (String videoId : page.videoIds()) {
                Optional<AiExtractionJobView> accepted = jobs.submitBackfillIfClaimActive(
                        run.runId(), run.leaseOwner(), run.channelId(), videoId);
                if (accepted.isEmpty()) return;
                AiExtractionJobView job = accepted.get();
                if (job.reused()) reused++; else submitted++;
            }
            runs.completePage(run.runId(), run.leaseOwner(), page.videoIds().size(), submitted, reused,
                    page.nextPageToken(), page.nextPageToken() == null, now());
            metrics.recordPage(page.nextPageToken() == null ? "completed" : "continued");
        } catch (YoutubeChannelVideoQueryException exception) {
            runs.fail(run.runId(), run.leaseOwner(), exception.category(), now());
            metrics.recordPage("failed");
        } catch (RuntimeException exception) {
            runs.fail(run.runId(), run.leaseOwner(), "BACKFILL_PROCESSING", now());
            metrics.recordPage("failed");
        }
    }

    private OffsetDateTime now() { return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
}
