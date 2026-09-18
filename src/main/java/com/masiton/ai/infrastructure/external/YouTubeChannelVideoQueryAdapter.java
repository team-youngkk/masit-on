package com.masiton.ai.infrastructure.external;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Component;

import com.masiton.ai.application.YoutubeChannelVideoQueryException;
import com.masiton.ai.application.port.out.YoutubeChannelVideoQueryPort;
import com.masiton.ai.application.port.out.YoutubeChannelBackfillQuotaPort;
import com.masiton.ai.application.port.out.YoutubeChannelBackfillQuotaUnavailableException;
import com.masiton.ai.infrastructure.worker.YoutubeChannelBackfillProperties;
import com.masiton.common.web.OriginCanonicalizer;

@Component
public class YouTubeChannelVideoQueryAdapter implements YoutubeChannelVideoQueryPort {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_RESPONSE_BYTES = 1_048_576;
    private static final int MAX_PAGE_TOKEN_LENGTH = 512;
    private static final int MAX_RESULTS = 50;
    private static final String ID_PATTERN = "[A-Za-z0-9_-]{1,128}";

    private final HttpClient httpClient;
    private final JsonParser parser = JsonParserFactory.getJsonParser();
    private final YoutubeChannelBackfillProperties properties;
    private final YoutubeChannelBackfillQuotaPort quota;
    private final URI baseUri;

    @Autowired
    public YouTubeChannelVideoQueryAdapter(YoutubeChannelBackfillProperties properties,
                                           YoutubeChannelBackfillQuotaPort quota) {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(), properties, quota);
    }

    public YouTubeChannelVideoQueryAdapter(YoutubeChannelBackfillProperties properties) {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(), properties, cost -> true);
    }

    YouTubeChannelVideoQueryAdapter(HttpClient httpClient, YoutubeChannelBackfillProperties properties) {
        this(httpClient, properties, cost -> true);
    }

    YouTubeChannelVideoQueryAdapter(HttpClient httpClient, YoutubeChannelBackfillProperties properties,
                                    YoutubeChannelBackfillQuotaPort quota) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.quota = quota;
        this.baseUri = requireAllowedOrigin(properties);
    }

    @Override
    public VideoPage query(String channelId, String pageToken) {
        return query(channelId, pageToken, MAX_RESULTS);
    }

    @Override
    public VideoPage query(String channelId, String pageToken, int maxResults) {
        validateId(channelId);
        validatePageToken(pageToken);
        if (maxResults < 1 || maxResults > MAX_RESULTS) {
            throw malformed();
        }

        Map<String, Object> channelResponse = get("/youtube/v3/channels?part=contentDetails&id="
                + encode(channelId), 1);
        String uploadsPlaylistId = uploadsPlaylist(channelResponse);
        if (uploadsPlaylistId == null) {
            throw malformed();
        }

        String pageQuery = "/youtube/v3/playlistItems?part=contentDetails,snippet&playlistId="
                + encode(uploadsPlaylistId) + "&maxResults=" + maxResults;
        if (pageToken != null) {
            pageQuery += "&pageToken=" + encode(pageToken);
        }
        Map<String, Object> playlistResponse = get(pageQuery, 1);
        return parseVideoPage(playlistResponse);
    }

    private Map<String, Object> get(String path, int cost) {
        try {
            if (!quota.tryReserve(cost)) {
                throw new YoutubeChannelVideoQueryException("YOUTUBE_QUOTA_EXCEEDED");
            }
            URI requestUri = baseUri.resolve(path + "&key=" + encode(properties.getApiKey()));
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(requestUri).timeout(RESPONSE_TIMEOUT).GET().build(),
                    boundedBodyHandler());
            if (response.headers().firstValueAsLong("Content-Length").orElse(0L) > MAX_RESPONSE_BYTES
                    || response.body() == null
                    || response.body().getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
                throw malformed();
            }
            int status = response.statusCode();
            if (status == 408) {
                throw new YoutubeChannelVideoQueryException("YOUTUBE_TIMEOUT");
            }
            if (status == 429) {
                throw new YoutubeChannelVideoQueryException("YOUTUBE_RATE_LIMIT");
            }
            if (status < 200 || status >= 300) {
                throw new YoutubeChannelVideoQueryException(status < 500 ? "YOUTUBE_4XX" : "YOUTUBE_5XX");
            }
            try {
                return parser.parseMap(response.body());
            } catch (RuntimeException exception) {
                throw new YoutubeChannelVideoQueryException("YOUTUBE_MALFORMED_RESPONSE", exception);
            }
        } catch (YoutubeChannelVideoQueryException exception) {
            throw exception;
        } catch (YoutubeChannelBackfillQuotaUnavailableException exception) {
            throw new YoutubeChannelVideoQueryException("YOUTUBE_QUOTA_UNAVAILABLE", exception);
        } catch (HttpTimeoutException exception) {
            throw new YoutubeChannelVideoQueryException("YOUTUBE_TIMEOUT", exception);
        } catch (IOException exception) {
            throw new YoutubeChannelVideoQueryException("YOUTUBE_UPSTREAM", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new YoutubeChannelVideoQueryException("YOUTUBE_UPSTREAM", exception);
        } catch (RuntimeException exception) {
            throw new YoutubeChannelVideoQueryException("YOUTUBE_MALFORMED_RESPONSE", exception);
        }
    }

    private String uploadsPlaylist(Map<String, Object> root) {
        Object rawItems = root.get("items");
        if (!(rawItems instanceof List<?> items) || items.size() != 1
                || !(items.getFirst() instanceof Map<?, ?> channel)
                || !(channel.get("contentDetails") instanceof Map<?, ?> contentDetails)
                || !(contentDetails.get("relatedPlaylists") instanceof Map<?, ?> related)) {
            return null;
        }
        Object rawPlaylist = related.get("uploads");
        return rawPlaylist instanceof String playlistId && playlistId.matches(ID_PATTERN)
                ? playlistId : null;
    }

    private VideoPage parseVideoPage(Map<String, Object> root) {
        Object rawItems = root.get("items");
        if (!(rawItems instanceof List<?> items) || items.size() > MAX_RESULTS) {
            throw malformed();
        }
        List<String> videoIds = new ArrayList<>(items.size());
        for (Object rawItem : items) {
            if (!(rawItem instanceof Map<?, ?> item)
                    || !(item.get("contentDetails") instanceof Map<?, ?> contentDetails)
                    || !(contentDetails.get("videoId") instanceof String videoId)
                    || !videoId.matches(ID_PATTERN)) {
                throw malformed();
            }
            videoIds.add(videoId);
        }

        Object rawNextPageToken = root.get("nextPageToken");
        if (rawNextPageToken != null && !(rawNextPageToken instanceof String)) {
            throw malformed();
        }
        String nextPageToken = rawNextPageToken instanceof String token && !token.isBlank() ? token : null;
        if (nextPageToken != null && nextPageToken.length() > MAX_PAGE_TOKEN_LENGTH) {
            throw malformed();
        }
        return new VideoPage(videoIds, nextPageToken);
    }

    private URI requireAllowedOrigin(YoutubeChannelBackfillProperties source) {
        try {
            String baseOrigin = OriginCanonicalizer.canonicalize(source.getBaseUrl());
            if (source.getAllowedOrigins() == null || source.getAllowedOrigins().stream()
                    .map(OriginCanonicalizer::canonicalize).noneMatch(baseOrigin::equals)) {
                throw new IllegalArgumentException();
            }
            return URI.create(baseOrigin);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("YouTube backfill origin is not allowed", exception);
        }
    }

    private void validateId(String value) {
        if (value == null || !value.matches(ID_PATTERN)) {
            throw new YoutubeChannelVideoQueryException("YOUTUBE_INVALID_IDENTIFIER");
        }
    }

    private void validatePageToken(String value) {
        if (value != null && (value.isBlank() || value.length() > MAX_PAGE_TOKEN_LENGTH)) {
            throw malformed();
        }
    }

    private YoutubeChannelVideoQueryException malformed() {
        return new YoutubeChannelVideoQueryException("YOUTUBE_MALFORMED_RESPONSE");
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private HttpResponse.BodyHandler<String> boundedBodyHandler() {
        return responseInfo -> new BoundedBodySubscriber(MAX_RESPONSE_BYTES);
    }

    private static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<String> {
        private final int limit;
        private final CompletableFuture<String> body = new CompletableFuture<>();
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        private boolean tooLarge;

        private BoundedBodySubscriber(int limit) {
            this.limit = limit;
        }

        @Override
        public CompletionStage<String> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (this.subscription != null) {
                subscription.cancel();
                return;
            }
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (tooLarge) return;
            long nextSize = output.size();
            for (ByteBuffer buffer : buffers) nextSize += buffer.remaining();
            if (nextSize > limit) {
                tooLarge = true;
                subscription.cancel();
                body.complete("");
                return;
            }
            for (ByteBuffer buffer : buffers) {
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                output.write(bytes, 0, bytes.length);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(output.toString(StandardCharsets.UTF_8));
        }
    }
}
