package com.masiton.ai.infrastructure.worker;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.masiton.ai.application.port.in.YoutubeChannelBackfillUseCase;

@Component
public class YoutubeChannelBackfillScheduler {
    private final YoutubeChannelBackfillUseCase backfill;
    public YoutubeChannelBackfillScheduler(YoutubeChannelBackfillUseCase backfill) { this.backfill = backfill; }
    @Scheduled(scheduler = "aiBackfillTaskScheduler", fixedDelayString = "${masiton.ai.youtube-backfill.poll-interval:PT30S}")
    public void poll() {
        backfill.scheduleDue();
        backfill.poll();
    }
}
