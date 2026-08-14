package com.masiton.ai.presentation.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("masiton.ai.youtube-webhook")
public class YoutubeWebhookProperties {

    private int maxPayloadBytes = 65_536;
    private String secret = "";
    private String subscriptionHubUrl = "https://pubsubhubbub.appspot.com/subscribe";
    private String callbackUrl = "http://localhost:8080/api/webhooks/youtube/channel-updates";

    public int getMaxPayloadBytes() {
        return maxPayloadBytes;
    }

    public void setMaxPayloadBytes(int maxPayloadBytes) {
        this.maxPayloadBytes = maxPayloadBytes;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getSubscriptionHubUrl() {
        return subscriptionHubUrl;
    }

    public void setSubscriptionHubUrl(String subscriptionHubUrl) {
        this.subscriptionHubUrl = subscriptionHubUrl;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }
}
