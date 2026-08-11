package com.masiton.ai.presentation.webhook;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
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
    public ResponseEntity<Void> receive(
            HttpServletRequest request,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature256,
            @RequestHeader(name = "X-Hub-Signature", required = false) String signature1) throws IOException {
        if (request.getContentLengthLong() > properties.getMaxPayloadBytes()) {
            throw tooLarge();
        }
        byte[] payload = readBounded(request.getInputStream());
        if (payload.length > properties.getMaxPayloadBytes()) {
            throw tooLarge();
        }
        verifySignature(payload, signature256 != null ? signature256 : signature1);
        YoutubeAtomNotificationParser.YoutubeAtomNotification notification = parser.parse(payload);
        useCase.submitWebhook(notification.channelId(), notification.videoId(), URI.create(notification.videoUrl()));
        return ResponseEntity.noContent().build();
    }

    private void verifySignature(byte[] payload, String signature) {
        String secret = properties.getSecret();
        if (secret == null || secret.isBlank() || signature == null || signature.isBlank()) {
            throw invalidSignature();
        }
        String trimmedSignature = signature.trim();
        String algorithm;
        String expectedPrefix;
        if (trimmedSignature.startsWith("sha256=")) {
            algorithm = "HmacSHA256";
            expectedPrefix = "sha256=";
        } else if (trimmedSignature.startsWith("sha1=")) {
            algorithm = "HmacSHA1";
            expectedPrefix = "sha1=";
        } else {
            throw invalidSignature();
        }
        String suppliedHex = trimmedSignature.substring(expectedPrefix.length());
        if (!suppliedHex.matches("[0-9a-fA-F]+")) {
            throw invalidSignature();
        }
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
            byte[] expected = mac.doFinal(payload);
            byte[] supplied = hexToBytes(suppliedHex);
            if (!MessageDigest.isEqual(expected, supplied)) {
                throw invalidSignature();
            }
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw invalidSignature();
        }
    }

    private byte[] hexToBytes(String value) {
        if ((value.length() & 1) != 0) {
            throw invalidSignature();
        }
        byte[] result = new byte[value.length() / 2];
        for (int index = 0; index < value.length(); index += 2) {
            int high = Character.digit(value.charAt(index), 16);
            int low = Character.digit(value.charAt(index + 1), 16);
            if (high < 0 || low < 0) {
                throw invalidSignature();
            }
            result[index / 2] = (byte) ((high << 4) | low);
        }
        return result;
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

    private com.masiton.common.web.BusinessException invalidSignature() {
        return new com.masiton.common.web.BusinessException(HttpStatus.FORBIDDEN,
                "AIEXTRACT_WEBHOOK_SIGNATURE_INVALID", "Webhook signature is invalid.");
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
