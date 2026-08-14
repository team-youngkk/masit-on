package com.masiton.restaurant.infrastructure.external;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/** Kakao 장소 URL의 기술 형식 검증과 HTTPS 정규화를 공유한다. */
final class KakaoPlaceUrlPolicy {

    private static final String KAKAO_PLACE_HOST = "place.map.kakao.com";

    private KakaoPlaceUrlPolicy() {
    }

    static Optional<URI> canonicalize(String placeUrl) {
        try {
            URI uri = URI.create(placeUrl);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
                return Optional.empty();
            }
            String host = uri.getHost();
            if (host == null || uri.getUserInfo() != null) {
                return Optional.empty();
            }
            int port = uri.getPort();
            if (port != -1 && port != defaultPort(scheme)) {
                return Optional.empty();
            }
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new URI("https", null, host, -1, path, uri.getQuery(), uri.getFragment()));
        } catch (IllegalArgumentException | URISyntaxException exception) {
            return Optional.empty();
        }
    }

    static boolean hasKakaoPlaceHost(URI uri) {
        return uri.getHost() != null && uri.getHost().equalsIgnoreCase(KAKAO_PLACE_HOST);
    }

    private static int defaultPort(String scheme) {
        return scheme.equalsIgnoreCase("https") ? 443 : 80;
    }
}
