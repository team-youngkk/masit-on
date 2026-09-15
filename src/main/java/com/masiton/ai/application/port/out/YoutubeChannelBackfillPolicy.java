package com.masiton.ai.application.port.out;

import java.time.Duration;

public interface YoutubeChannelBackfillPolicy {

    boolean isEnabled();

    Duration getLeaseDuration();

    int getMaxPagesPerRun();

    int getMaxVideosPerRun();
}
