package com.masiton.ai.application.port.out.dto;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record AiVideoExtractionRequest(URI videoUrl, String supplementText) {

    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final int MAX_VIDEO_URL_LENGTH = 2_048;
    private static final int MAX_SUPPLEMENT_LENGTH = 20_000;

    public AiVideoExtractionRequest {
        Objects.requireNonNull(videoUrl, "videoUrl must not be null");
        if (videoUrl.toString().length() > MAX_VIDEO_URL_LENGTH || !isPublicYoutubeUrl(videoUrl)) {
            throw new IllegalArgumentException("videoUrl must be a public YouTube HTTPS URL");
        }
        supplementText = supplementText == null ? "" : supplementText.trim();
        if (supplementText.length() > MAX_SUPPLEMENT_LENGTH) {
            throw new IllegalArgumentException("supplementText is too long");
        }
    }

    private static boolean isPublicYoutubeUrl(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getPort() != -1
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            return false;
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if ("youtu.be".equals(host)) {
            return pathIdentifier(uri.getPath(), "/");
        }
        if (!"youtube.com".equals(host) && !"www.youtube.com".equals(host)) {
            return false;
        }
        String path = uri.getPath();
        if ("/watch".equals(path)) {
            return queryVideoId(uri.getQuery());
        }
        if (path != null && path.startsWith("/shorts/")) {
            return pathIdentifier(path.substring("/shorts/".length()), "");
        }
        if (path != null && path.startsWith("/embed/")) {
            return pathIdentifier(path.substring("/embed/".length()), "");
        }
        return false;
    }

    private static boolean queryVideoId(String query) {
        if (query == null) {
            return false;
        }
        String videoId = null;
        for (String parameter : query.split("&", -1)) {
            if (!parameter.startsWith("v=")) {
                continue;
            }
            if (videoId != null || !VIDEO_ID_PATTERN.matcher(parameter.substring(2)).matches()) {
                return false;
            }
            videoId = parameter.substring(2);
        }
        return videoId != null;
    }

    private static boolean pathIdentifier(String path, String prefix) {
        if (path == null || !path.startsWith(prefix)) {
            return false;
        }
        String identifier = path.substring(prefix.length());
        return VIDEO_ID_PATTERN.matcher(identifier).matches();
    }
}
