package com.masiton.ai.application;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.ai.application.port.in.AiExtractionJobUseCase;
import com.masiton.ai.application.port.out.AiExtractionJobStore;
import com.masiton.ai.application.port.out.TemporaryInputCipher;
import com.masiton.ai.application.port.out.TemporaryInputCipher.EncryptedInput;
import com.masiton.ai.application.port.out.YoutubeChannelWatchStore;
import com.masiton.ai.application.port.out.dto.AiExtractionJobView;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.video.application.port.in.ResolveVerifiedVideoUseCase;
import com.masiton.video.application.port.out.VerifiedVideo;

@Service
public class AiExtractionJobService implements AiExtractionJobUseCase {

    private static final Pattern CHANNEL_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final String INVALID_VIDEO_URL_CODE = "AIEXTRACT_INVALID_VIDEO_URL";

    private final ResolveVerifiedVideoUseCase verifiedVideoResolver;
    private final AiExtractionJobPersistenceService persistence;
    private final YoutubeChannelWatchStore watchStore;
    private final TemporaryInputCipher temporaryInputCipher;

    public AiExtractionJobService(ResolveVerifiedVideoUseCase verifiedVideoResolver,
                                  AiExtractionJobPersistenceService persistence,
                                  YoutubeChannelWatchStore watchStore,
                                  TemporaryInputCipher temporaryInputCipher) {
        this.verifiedVideoResolver = verifiedVideoResolver;
        this.persistence = persistence;
        this.watchStore = watchStore;
        this.temporaryInputCipher = temporaryInputCipher;
    }

    @Override
    public AiExtractionJobView submitAdmin(String rawVideoUrl, String rawSupplementText, String idempotencyKey) {
        return submitAdmin(rawVideoUrl, rawSupplementText, idempotencyKey, null, null);
    }

    @Override
    public AiExtractionJobView submitRetry(String rawVideoUrl, String rawSupplementText, String rawReason) {
        String reason = rawReason == null ? "" : rawReason.trim();
        if (reason.isBlank() || reason.length() > 1_000) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "reason", "reason is required and must be at most 1,000 characters.");
        }
        return submitAdmin(rawVideoUrl, rawSupplementText, null, UUID.randomUUID().toString(), reason);
    }

    private AiExtractionJobView submitAdmin(String rawVideoUrl, String rawSupplementText, String idempotencyKey,
                                            String retryNonce, String retryReason) {
        URI requestedUrl = youtubeUrl(rawVideoUrl);
        URI canonicalRequestedUrl = canonicalYoutubeUrl(videoIdFrom(requestedUrl));
        String supplement = rawSupplementText == null ? "" : rawSupplementText.trim();
        if (supplement.length() > 20_000) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "supplementText", "supplementText is too long.");
        }
        String normalizedIdempotencyKey = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (normalizedIdempotencyKey.length() > 200) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "idempotencyKey", "idempotencyKey is too long.");
        }
        String requestedVideoId = videoIdFrom(requestedUrl);
        String inputMode = supplement.isBlank() ? "GEMINI_VIDEO_URL" : "ADMIN_TEXT";
        byte[] inputHash = hash(canonicalRequestedUrl.toString(), supplement, retryNonce);
        Optional<AiExtractionJobView> existing = Optional.empty();
        if (normalizedIdempotencyKey.isEmpty() && inputMode.equals("GEMINI_VIDEO_URL")) {
            existing = persistence.findByVideoIdAndInputMode(requestedVideoId, inputMode,
                    AiExtractionContract.PROVIDER, AiExtractionContract.MODEL_VERSION,
                    AiExtractionContract.PROMPT_VERSION, AiExtractionContract.SCHEMA_VERSION);
        }
        if (existing.isEmpty()) {
            // Keep inputHash independent from the client key so ADMIN and WEBHOOK share one job.
            // A keyed request must use the exact normalized payload hash and cannot use the
            // input-mode shortcut, which could otherwise replay a different payload.
            existing = persistence.findByVideoIdAndInputHash(requestedVideoId, inputHash,
                    AiExtractionContract.PROVIDER, AiExtractionContract.MODEL_VERSION,
                    AiExtractionContract.PROMPT_VERSION, AiExtractionContract.SCHEMA_VERSION);
        }
        if (existing.isPresent()) {
            return existing.get();
        }
        VerifiedVideo verified = verifiedVideoResolver.resolve(requestedUrl)
                .orElseThrow(this::invalidVideoUrl);
        String channelId = requiredId(verified.publisherExternalChannelId(), "channelId");
        String videoId = requiredId(verified.externalVideoId(), "videoId");
        URI videoUrl = canonicalYoutubeUrl(videoId);
        Optional<EncryptedInput> encrypted = supplement.isBlank()
                ? Optional.empty()
                : Optional.of(temporaryInputCipher.encrypt(supplement));
        return create("ADMIN", "BACKFILL", channelId, videoId, videoUrl, inputMode, inputHash, encrypted, retryReason);
    }

    @Override
    @Transactional(timeout = 5)
    public Optional<AiExtractionJobView> submitWebhook(String channelId, String videoId, URI videoUrl) {
        String normalizedChannelId = requiredId(channelId, "channelId");
        String normalizedVideoId = requiredId(videoId, "videoId");
        URI normalizedUrl = youtubeUrl(videoUrl.toString());
        if (!normalizedVideoId.equals(videoIdFrom(normalizedUrl))) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "videoUrl", "videoUrl does not match videoId.");
        }
        URI canonicalVideoUrl = canonicalYoutubeUrl(normalizedVideoId);
        YoutubeChannelWatchStore.Watch watch = watchStore.findForUpdate(normalizedChannelId).orElse(null);
        if (watch == null || !watch.acceptsNotifications()) {
            return Optional.empty();
        }
        AiExtractionJobView job = create("WEBHOOK", "REALTIME", normalizedChannelId, normalizedVideoId,
                canonicalVideoUrl, "GEMINI_VIDEO_URL", hash(canonicalVideoUrl.toString(), "", null), Optional.empty(), null);
        watchStore.markNotificationReceived(normalizedChannelId, OffsetDateTime.now(ZoneOffset.UTC));
        return Optional.of(job);
    }

    @Override
    @Transactional(timeout = 5)
    public String verifyChallenge(String channelId, String verifyToken, String challenge) {
        String normalizedChannelId = requiredId(channelId, "channelId");
        if (challenge == null || challenge.isBlank() || challenge.length() > 512) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "hub.challenge", "hub.challenge is invalid.");
        }
        YoutubeChannelWatchStore.Watch watch = watchStore.findForUpdate(normalizedChannelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!watch.enabled()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "AIEXTRACT_WEBHOOK_TOKEN_INVALID", "Webhook token is invalid.");
        }
        if (verifyToken == null || !constantTimeHashEquals(watch.subscriptionTokenHash(), verifyToken)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "AIEXTRACT_WEBHOOK_TOKEN_INVALID", "Webhook token is invalid.");
        }
        watchStore.markSubscriptionVerified(normalizedChannelId, OffsetDateTime.now(ZoneOffset.UTC));
        return challenge;
    }
    private AiExtractionJobView create(String source, String priority, String channelId, String videoId,
                                       URI videoUrl, String inputMode, byte[] inputHash,
                                       Optional<EncryptedInput> encrypted, String retryReason) {
        AiExtractionJobStore.AiExtractionJobDraft draft = new AiExtractionJobStore.AiExtractionJobDraft(
                java.util.UUID.randomUUID(), source, priority, channelId, videoId, videoUrl, inputMode,
                inputHash, AiExtractionContract.PROVIDER,
                AiExtractionContract.MODEL_VERSION, AiExtractionContract.PROMPT_VERSION,
                AiExtractionContract.SCHEMA_VERSION, OffsetDateTime.now(ZoneOffset.UTC), retryReason);
        return persistence.create(draft, encrypted);
    }

    private URI youtubeUrl(String value) {
        if (value == null || value.isBlank()) {
            throw invalidVideoUrl();
        }
        try {
            String trimmed = value.trim();
            if (trimmed.length() > 2_048) {
                throw new IllegalArgumentException();
            }
            URI uri = URI.create(trimmed);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getPort() != -1
                    || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null
                    || !(host.equals("youtu.be") || host.equals("youtube.com") || host.equals("www.youtube.com"))
                    || videoIdFrom(uri) == null) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw invalidVideoUrl();
        }
    }

    private BusinessException invalidVideoUrl() {
        return new BusinessException(HttpStatus.BAD_REQUEST, INVALID_VIDEO_URL_CODE,
                "YouTube videoUrl is invalid.");
    }

    private URI canonicalYoutubeUrl(String videoId) {
        String normalizedVideoId = validId(videoId);
        if (normalizedVideoId == null) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "videoUrl", "videoUrl is invalid.");
        }
        return URI.create("https://www.youtube.com/watch?v=" + normalizedVideoId);
    }

    private String videoIdFrom(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (host.equals("youtu.be")) {
            return segment(uri.getPath(), 1, 2);
        }
        if ("/watch".equals(uri.getPath()) && uri.getQuery() != null) {
            String videoId = null;
            for (String part : uri.getQuery().split("&")) {
                if (part.startsWith("v=") && part.length() > 2) {
                    if (videoId != null) {
                        return null;
                    }
                    videoId = validId(part.substring(2));
                }
            }
            return videoId;
        }
        if (uri.getPath() != null && uri.getPath().startsWith("/shorts/")) {
            return segment(uri.getPath(), 2, 3);
        }
        if (uri.getPath() != null && uri.getPath().startsWith("/embed/")) {
            return segment(uri.getPath(), 2, 3);
        }
        return null;
    }

    private String segment(String path, int index, int expectedSegmentCount) {
        if (path == null) return null;
        String[] segments = path.split("/", -1);
        return segments.length == expectedSegmentCount ? validId(segments[index]) : null;
    }

    private String validId(String value) {
        return value != null && VIDEO_ID_PATTERN.matcher(value).matches() ? value : null;
    }

    private String requiredId(String value, String field) {
        if (value == null || !CHANNEL_ID_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, field, field + " is invalid.");
        }
        return value;
    }

    private byte[] hash(String videoUrl, String supplement, String nonce) {
        try {
            String suffix = nonce == null ? "" : "\nretry:" + nonce;
            return MessageDigest.getInstance("SHA-256")
                    .digest((videoUrl.trim() + "\n" + supplement.trim() + suffix).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private boolean constantTimeHashEquals(byte[] expectedHash, String token) {
        if (expectedHash == null) return false;
        return MessageDigest.isEqual(expectedHash, hashToken(token));
    }

    private byte[] hashToken(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
