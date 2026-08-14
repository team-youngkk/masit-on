package com.masiton.ai.infrastructure.external;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.masiton.ai.application.YoutubeChannelWatchSubscriptionFailedException;
import com.masiton.ai.application.port.out.YoutubeChannelWatchSubscriptionPort;
import com.masiton.ai.presentation.webhook.YoutubeWebhookProperties;

@Component
public class PubSubHubbubYoutubeChannelWatchSubscriptionAdapter implements YoutubeChannelWatchSubscriptionPort {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(5);
    private static final String TOPIC_URL = "https://www.youtube.com/xml/feeds/videos.xml?channel_id=";

    private final HttpClient httpClient;
    private final YoutubeWebhookProperties properties;
    private final Duration responseTimeout;

    @Autowired
    public PubSubHubbubYoutubeChannelWatchSubscriptionAdapter(YoutubeWebhookProperties properties) {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(), properties, RESPONSE_TIMEOUT);
    }

    PubSubHubbubYoutubeChannelWatchSubscriptionAdapter(HttpClient httpClient, YoutubeWebhookProperties properties) {
        this(httpClient, properties, RESPONSE_TIMEOUT);
    }

    PubSubHubbubYoutubeChannelWatchSubscriptionAdapter(HttpClient httpClient, YoutubeWebhookProperties properties,
                                                       Duration responseTimeout) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.responseTimeout = responseTimeout;
    }

    @Override
    public void subscribe(String channelId, String verificationToken) {
        String form = formBody(channelId, verificationToken);
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getSubscriptionHubUrl()))
                .timeout(responseTimeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new YoutubeChannelWatchSubscriptionFailedException(statusCategory(response.statusCode()));
            }
        } catch (HttpTimeoutException exception) {
            throw new YoutubeChannelWatchSubscriptionFailedException("SUBSCRIPTION_TIMEOUT", exception);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new YoutubeChannelWatchSubscriptionFailedException("SUBSCRIPTION_UPSTREAM", exception);
        }
    }

    private String statusCategory(int statusCode) {
        if (statusCode >= 400 && statusCode < 500) {
            return "SUBSCRIPTION_4XX";
        }
        if (statusCode >= 500) {
            return "SUBSCRIPTION_5XX";
        }
        return "SUBSCRIPTION_UNEXPECTED_STATUS";
    }

    private String formBody(String channelId, String verificationToken) {
        return parameter("hub.mode", "subscribe")
                + "&" + parameter("hub.topic", TOPIC_URL + encode(channelId))
                + "&" + parameter("hub.callback", properties.getCallbackUrl())
                + "&" + parameter("hub.verify", "async")
                + "&" + parameter("hub.verify_token", verificationToken)
                + "&" + parameter("hub.secret", properties.getSecret());
    }

    private String parameter(String name, String value) {
        return encode(name) + "=" + encode(value);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
