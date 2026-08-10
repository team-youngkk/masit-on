package com.masiton.ai.presentation.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("masiton.ai.youtube-webhook")
public class YoutubeWebhookProperties {

    private int maxPayloadBytes = 65_536;

    public int getMaxPayloadBytes() {
        return maxPayloadBytes;
    }

    public void setMaxPayloadBytes(int maxPayloadBytes) {
        this.maxPayloadBytes = maxPayloadBytes;
    }
}
