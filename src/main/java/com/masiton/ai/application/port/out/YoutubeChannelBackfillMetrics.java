package com.masiton.ai.application.port.out;

public interface YoutubeChannelBackfillMetrics {

    void recordRunStart(boolean reused);

    void recordPage(String outcome);
}
