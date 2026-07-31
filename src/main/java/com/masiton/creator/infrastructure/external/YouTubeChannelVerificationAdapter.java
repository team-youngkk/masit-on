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
            return Optional.of(new VerifiedChannel(id, title, canonicalUrl(id), OffsetDateTime.now()));
        } catch (JacksonException exception) { throw new ChannelVerificationFailedException(exception); }
    }
    private String canonicalUrl(String id) { return "https://www.youtube.com/channel/" + id; }
    private String stringValue(Object value) { return value instanceof String string && !string.trim().isEmpty() ? string.trim() : null; }
}
