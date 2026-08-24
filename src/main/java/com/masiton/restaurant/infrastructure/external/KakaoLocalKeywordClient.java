package com.masiton.restaurant.infrastructure.external;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.masiton.common.web.OriginCanonicalizer;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Kakao Local keyword API의 HTTP·인증·JSON 문서 경계를 공유한다. */
@Component
class KakaoLocalKeywordClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(5);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUri;
    private final String restApiKey;

    @Autowired
    KakaoLocalKeywordClient(
            ObjectMapper objectMapper,
            @Value("${masiton.integration.kakao.base-url}") String baseUrl,
            @Value("${masiton.integration.kakao.rest-api-key:}") String restApiKey,
            @Value("${masiton.integration.kakao.allowed-origins:}") String allowedOrigins
    ) {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(), objectMapper, baseUrl, restApiKey, allowedOrigins);
    }

    KakaoLocalKeywordClient(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl, String restApiKey) {
        this(httpClient, objectMapper, baseUrl, restApiKey, "");
    }

    KakaoLocalKeywordClient(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl, String restApiKey,
                            String allowedOrigins) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUri = requireBaseUri(baseUrl, allowedOrigins);
        this.restApiKey = restApiKey;
    }

    private static URI requireBaseUri(String baseUrl, String allowedOrigins) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Kakao Local endpoint must be configured");
        }
        try {
            URI uri = URI.create(baseUrl.trim());
            String scheme = uri.getScheme();
            boolean http = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            boolean rootPath = uri.getPath() == null || uri.getPath().isBlank() || "/".equals(uri.getPath());
            if (!uri.isAbsolute() || !http || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null || !rootPath
                    || uri.getPort() == 0 || uri.getPort() > 65535 || !isAllowedOrigin(uri, allowedOrigins)) {
                throw new IllegalStateException("Kakao Local endpoint must be an HTTP(S) origin");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Kakao Local endpoint is malformed", exception);
        }
    }

    private static boolean isAllowedOrigin(URI uri, String allowedOrigins) {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            return true;
        }
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .anyMatch(origin -> OriginCanonicalizer.matches(uri.toString(), origin));
    }

    KakaoKeywordResponse search(String name) {
        try {
            String query = URLEncoder.encode(name, StandardCharsets.UTF_8);
            URI requestUri = baseUri.resolve("/v2/local/search/keyword.json?query=" + query);
            HttpRequest.Builder request = HttpRequest.newBuilder(requestUri)
                    .timeout(RESPONSE_TIMEOUT)
                    .GET();
            if (!restApiKey.isBlank()) {
                request.header("Authorization", "KakaoAK " + restApiKey);
            }

            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new KakaoKeywordResponse(response.statusCode(), 0, List.of());
            }
            return parse(response.statusCode(), response.body());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new KakaoLocalKeywordClientException(exception);
        } catch (RuntimeException exception) {
            if (exception instanceof KakaoLocalKeywordClientException) {
                throw exception;
            }
            throw new KakaoLocalKeywordClientException(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private KakaoKeywordResponse parse(int statusCode, String responseBody) {
        try {
            Map<String, Object> payload = objectMapper.readValue(responseBody, MAP_TYPE);
            Object documentsValue = payload.get("documents");
            if (!(documentsValue instanceof List<?> documents)) {
                throw new KakaoLocalKeywordClientException();
            }
            List<Map<String, Object>> mappedDocuments = documents.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .map(document -> (Map<String, Object>) document)
                    .toList();
            return new KakaoKeywordResponse(statusCode, documents.size(), mappedDocuments);
        } catch (JacksonException exception) {
            throw new KakaoLocalKeywordClientException(exception);
        }
    }

    record KakaoKeywordResponse(int statusCode, int documentCount, List<Map<String, Object>> documents) {
    }

    static final class KakaoLocalKeywordClientException extends RuntimeException {
        KakaoLocalKeywordClientException() {
        }

        KakaoLocalKeywordClientException(Throwable cause) {
            super(cause);
        }
    }
}
