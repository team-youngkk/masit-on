package com.masiton.ai.infrastructure.external;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    @Autowired
    public PubSubHubbubYoutubeChannelWatchSubscriptionAdapter(YoutubeWebhookProperties properties) {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(), properties);
    }

    PubSubHubbubYoutubeChannelWatchSubscriptionAdapter(HttpClient httpClient, YoutubeWebhookProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
    }

    @Override
    public void subscribe(String channelId, String verificationToken) {
        String form = formBody(channelId, verificationToken);
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getSubscriptionHubUrl()))
                .timeout(RESPONSE_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new YoutubeChannelWatchSubscriptionFailedException();
            }
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new YoutubeChannelWatchSubscriptionFailedException(exception);
        }
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
