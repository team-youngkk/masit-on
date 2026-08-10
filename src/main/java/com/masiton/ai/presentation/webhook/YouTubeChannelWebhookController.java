package com.masiton.ai.presentation.webhook;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.ai.application.port.in.AiExtractionJobUseCase;

@RestController
@RequestMapping("/api/webhooks/youtube/channel-updates")
public class YouTubeChannelWebhookController {

    private final YoutubeAtomNotificationParser parser;
    private final YoutubeWebhookProperties properties;
    private final AiExtractionJobUseCase useCase;

    public YouTubeChannelWebhookController(YoutubeAtomNotificationParser parser,
                                           YoutubeWebhookProperties properties,
                                           AiExtractionJobUseCase useCase) {
        this.parser = parser;
        this.properties = properties;
        this.useCase = useCase;
    }

    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.topic") String topic,
            @RequestParam(name = "hub.verify_token") String verifyToken,
            @RequestParam(name = "hub.challenge") String challenge) {
        String channelId = channelIdFromTopic(topic);
        return ResponseEntity.ok(useCase.verifyChallenge(channelId, verifyToken, challenge));
    }

    @PostMapping(consumes = "application/atom+xml")
    public ResponseEntity<Void> receive(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > properties.getMaxPayloadBytes()) {
            throw tooLarge();
        }
        byte[] payload = readBounded(request.getInputStream());
        if (payload.length > properties.getMaxPayloadBytes()) {
            throw tooLarge();
        }
        YoutubeAtomNotificationParser.YoutubeAtomNotification notification = parser.parse(payload);
        useCase.submitWebhook(notification.channelId(), notification.videoId(), URI.create(notification.videoUrl()));
        return ResponseEntity.noContent().build();
    }

    private byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(properties.getMaxPayloadBytes(), 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > properties.getMaxPayloadBytes()) throw tooLarge();
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private com.masiton.common.web.BusinessException tooLarge() {
        return new com.masiton.common.web.BusinessException(HttpStatus.PAYLOAD_TOO_LARGE,
                "AIEXTRACT_WEBHOOK_PAYLOAD_TOO_LARGE", "Webhook payload is too large.");
    }

    private String channelIdFromTopic(String topic) {
        try {
            URI uri = URI.create(topic);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getQuery() == null) throw new IllegalArgumentException();
            for (String part : uri.getQuery().split("&")) {
                int separator = part.indexOf('=');
                if (separator > 0 && "channel_id".equals(URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8))) {
                    String value = URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8);
                    if (!value.isBlank()) return value;
                }
            }
        } catch (IllegalArgumentException exception) {
            // The common exception handler returns the traceId without echoing the topic.
        }
        throw new com.masiton.common.web.BusinessException(HttpStatus.BAD_REQUEST,
                "AIEXTRACT_INVALID_WEBHOOK_TOPIC", "Webhook topic is invalid.");
    }
}
