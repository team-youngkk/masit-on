package com.masiton.video.infrastructure.external;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.masiton.video.application.VideoVerificationFailedException;
import com.masiton.video.application.port.out.VerifiedVideo;
import com.masiton.video.application.port.out.VideoVerificationPort;
import com.masiton.common.web.OriginCanonicalizer;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
class YouTubeVideoVerificationAdapter implements VideoVerificationPort {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(5);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private final HttpClient httpClient; private final ObjectMapper objectMapper; private final URI baseUri; private final String apiKey;
    @Autowired
    YouTubeVideoVerificationAdapter(ObjectMapper objectMapper,
                                    @Value("${masiton.integration.youtube.base-url}") String baseUrl,
                                    @Value("${masiton.integration.youtube.api-key:}") String apiKey,
                                    @Value("${masiton.integration.youtube.allowed-origins:}") String allowedOrigins) {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(), objectMapper, baseUrl, apiKey, allowedOrigins);
    }
    YouTubeVideoVerificationAdapter(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl, String apiKey) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUri = requireBaseUri(baseUrl, "", false);
        this.apiKey = apiKey;
    }
    YouTubeVideoVerificationAdapter(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl, String apiKey,
                                    String allowedOrigins) {
        this.httpClient = httpClient; this.objectMapper = objectMapper;
        this.baseUri = requireBaseUri(baseUrl, allowedOrigins, true); this.apiKey = apiKey;
    }

    private static URI requireBaseUri(String baseUrl, String allowedOrigins, boolean requireAllowedOrigin) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("YouTube verification endpoint must be configured");
        }
        try {
            URI uri = URI.create(baseUrl.trim());
            String scheme = uri.getScheme();
            boolean http = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            boolean rootPath = uri.getPath() == null || uri.getPath().isBlank() || "/".equals(uri.getPath());
            if (!uri.isAbsolute() || !http || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null || !rootPath
                    || uri.getPort() == 0 || uri.getPort() > 65535
                    || !isAllowedOrigin(uri, allowedOrigins, requireAllowedOrigin)) {
                throw new IllegalStateException("YouTube verification endpoint must be an HTTP(S) origin");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("YouTube verification endpoint is malformed", exception);
        }
    }

    private static boolean isAllowedOrigin(URI uri, String allowedOrigins, boolean requireAllowedOrigin) {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            return !requireAllowedOrigin;
        }
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .anyMatch(origin -> OriginCanonicalizer.matches(uri.toString(), origin));
    }
    @Override
    public Optional<VerifiedVideo> verify(URI sourceUrl) {
        String videoId = videoId(sourceUrl);
        URI requestUri = baseUri.resolve("/youtube/v3/videos?part=snippet&id=" + URLEncoder.encode(videoId, StandardCharsets.UTF_8) + "&key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
        try {
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(requestUri).timeout(RESPONSE_TIMEOUT).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) return Optional.empty();
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new VideoVerificationFailedException();
            return parse(response.body(), videoId);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new VideoVerificationFailedException(exception);
        }
    }
    private String videoId(URI sourceUrl) {
        String host = sourceUrl.getHost().toLowerCase();
        if (host.equals("youtu.be")) {
            String path = sourceUrl.getPath(); if (path == null || path.length() <= 1) throw new VideoVerificationFailedException();
            return path.substring(1);
        }
        String path = sourceUrl.getPath();
        if (path != null && path.startsWith("/shorts/") && path.length() > 8) return path.substring(8);
        if (path != null && path.startsWith("/embed/") && path.length() > 7) return path.substring(7);
        String query = sourceUrl.getQuery();
        if (query != null) for (String part : query.split("&")) if (part.startsWith("v=") && part.length() > 2) return part.substring(2);
        throw new VideoVerificationFailedException();
    }
    @SuppressWarnings("unchecked")
    private Optional<VerifiedVideo> parse(String body, String expectedVideoId) {
        try {
            Map<String, Object> payload = objectMapper.readValue(body, MAP_TYPE);
            Object itemsValue = payload.get("items");
            if (!(itemsValue instanceof List<?> items) || items.isEmpty() || !(items.getFirst() instanceof Map<?, ?> item)) return Optional.empty();
            if (!(item.get("id") instanceof String id) || !expectedVideoId.equals(id) || !(item.get("snippet") instanceof Map<?, ?> rawSnippet)) throw new VideoVerificationFailedException();
            Map<String, Object> snippet = (Map<String, Object>) rawSnippet;
            String title = value(snippet.get("title")); String channelId = value(snippet.get("channelId")); String channelTitle = value(snippet.get("channelTitle"));
            String publishedAt = value(snippet.get("publishedAt")); String thumbnailUrl = thumbnail(snippet.get("thumbnails"));
            if (title == null || channelId == null || channelTitle == null || publishedAt == null || thumbnailUrl == null) throw new VideoVerificationFailedException();
            return Optional.of(new VerifiedVideo(id, channelId, title, thumbnailUrl, channelTitle, "https://www.youtube.com/watch?v=" + id,
                    OffsetDateTime.parse(publishedAt), OffsetDateTime.now()));
        } catch (JacksonException | IllegalArgumentException exception) { throw new VideoVerificationFailedException(exception); }
    }
    @SuppressWarnings("unchecked")
    private String thumbnail(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return null; Map<String, Object> thumbnails = (Map<String, Object>) raw;
        for (String key : List.of("high", "medium", "default")) if (thumbnails.get(key) instanceof Map<?, ?> image && image.get("url") instanceof String url && !url.isBlank()) return url;
        return null;
    }
    private String value(Object value) { return value instanceof String string && !string.trim().isEmpty() ? string.trim() : null; }
}
