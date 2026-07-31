package com.masiton.creator.infrastructure.external;

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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.masiton.creator.application.ChannelVerificationFailedException;
import com.masiton.creator.application.port.out.ChannelVerificationPort;
import com.masiton.creator.application.port.out.VerifiedChannel;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
class YouTubeChannelVerificationAdapter implements ChannelVerificationPort {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(5);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUri;
    private final String apiKey;

    @Autowired
    YouTubeChannelVerificationAdapter(ObjectMapper objectMapper,
                                      @Value("${masiton.integration.youtube.base-url:https://www.googleapis.com}") String baseUrl,
                                      @Value("${masiton.integration.youtube.api-key:}") String apiKey) {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(), objectMapper, baseUrl, apiKey);
    }
    YouTubeChannelVerificationAdapter(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl, String apiKey) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUri = URI.create(baseUrl);
        this.apiKey = apiKey;
    }
    @Override
    public Optional<VerifiedChannel> verify(URI channelUrl) {
        String lookup = channelLookup(channelUrl);
        URI requestUri = baseUri.resolve("/youtube/v3/channels?part=snippet&" + lookup + "&key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
        try {
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(requestUri).timeout(RESPONSE_TIMEOUT).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) return Optional.empty();
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new ChannelVerificationFailedException();
            return parse(response.body(), channelUrl);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ChannelVerificationFailedException(exception);
        }
    }
    private String channelLookup(URI channelUrl) {
        String path = channelUrl.getPath();
        String[] segments = path.split("/");
        String last = segments.length == 0 ? "" : segments[segments.length - 1];
        if (path.startsWith("/channel/") && !last.isBlank()) return "id=" + URLEncoder.encode(last, StandardCharsets.UTF_8);
        if (path.startsWith("/@") && !last.isBlank()) return "forHandle=" + URLEncoder.encode(last, StandardCharsets.UTF_8);
        throw new ChannelVerificationFailedException();
    }
    @SuppressWarnings("unchecked")
    private Optional<VerifiedChannel> parse(String body, URI submittedUrl) {
        try {
            Map<String, Object> payload = objectMapper.readValue(body, MAP_TYPE);
            Object itemsValue = payload.get("items");
            if (!(itemsValue instanceof List<?> items) || items.isEmpty() || !(items.getFirst() instanceof Map<?, ?> item)) return Optional.empty();
            Object idValue = item.get("id");
            Object snippetValue = item.get("snippet");
            if (!(idValue instanceof String id) || !(snippetValue instanceof Map<?, ?> rawSnippet)) throw new ChannelVerificationFailedException();
            Map<String, Object> snippet = (Map<String, Object>) rawSnippet;
            String title = stringValue(snippet.get("title"));
            if (id.isBlank() || title == null) throw new ChannelVerificationFailedException();
            String handle = stringValue(snippet.get("customUrl"));
            String description = stringValue(snippet.get("description"));
            String profileImageUrl = thumbnailUrl(snippet.get("thumbnails"));
            return Optional.of(new VerifiedChannel(
                    id, title, canonicalUrl(id), profileImageUrl, description, handle, OffsetDateTime.now()));
        } catch (JacksonException exception) { throw new ChannelVerificationFailedException(exception); }
    }
    private String canonicalUrl(String id) { return "https://www.youtube.com/channel/" + id; }
    private String stringValue(Object value) { return value instanceof String string && !string.trim().isEmpty() ? string.trim() : null; }

    /**
     * 표시용 프로필 이미지는 선택 값이므로 채널 검증 실패로 넓히지 않는다. high/medium/default
     * 순서로 존재하는 첫 썸네일만 선택한다. creator 테이블 CHECK 제약이 비어 있지 않은 HTTPS
     * URL만 허용하므로 선택된 URL이 HTTPS가 아니면 다음 우선순위로 넘기지 않고 즉시 null로
     * 떨어뜨린다.
     */
    @SuppressWarnings("unchecked")
    private String thumbnailUrl(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return null;
        Map<String, Object> thumbnails = (Map<String, Object>) raw;
        for (String key : List.of("high", "medium", "default")) {
            if (thumbnails.get(key) instanceof Map<?, ?> image) {
                String url = stringValue(image.get("url"));
                if (url != null) {
                    return isHttps(url) ? url : null;
                }
            }
        }
        return null;
    }
    /**
     * ck_creator__profile_image_url_https는 {@code LIKE 'https://%'}로 대소문자와 슬래시 두 개를
     * 문자 그대로 요구한다. URI scheme 비교는 {@code https:/host}·{@code HTTPS://host}까지 통과시켜
     * 제약을 위반하는 값을 저장 경로로 흘려보내므로 제약과 같은 형태로 검사한다.
     */
    private boolean isHttps(String url) {
        return url.startsWith("https://");
    }
}
