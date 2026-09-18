package com.masiton.ai.infrastructure.worker;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.masiton.ai.application.port.out.YoutubeChannelBackfillMetrics;

import io.micrometer.core.instrument.MeterRegistry;

@Component
public class MicrometerYoutubeChannelBackfillMetrics implements YoutubeChannelBackfillMetrics {

    private final Optional<MeterRegistry> registry;

    public MicrometerYoutubeChannelBackfillMetrics(Optional<MeterRegistry> registry) {
        this.registry = registry;
    }

    @Override
    public void recordRunStart(boolean reused) {
        registry.ifPresent(value -> value.counter("masiton.ai.youtube_backfill.run",
                "outcome", reused ? "reused" : "created").increment());
    }

    @Override
    public void recordPage(String outcome) {
        registry.ifPresent(value -> value.counter("masiton.ai.youtube_backfill.page",
                "outcome", outcome).increment());
    }
}
