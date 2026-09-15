package com.masiton.ai.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.masiton.ai.application.YoutubeChannelVideoQueryException;
import com.masiton.ai.application.port.out.YoutubeChannelVideoQueryPort;
import com.masiton.ai.infrastructure.worker.YoutubeChannelBackfillProperties;

@DisplayName("YouTube 채널 영상 목록 조회 Adapter")
class YouTubeChannelVideoQueryAdapterTest {

    private final HttpClient httpClient = mock(HttpClient.class);

    @Test
    @DisplayName("채널 업로드 목록 조회는 channels와 playlistItems API를 순서대로 호출한다")
    void 채널영상조회_정상응답_업로드Playlist와영상목록을조회한다() throws Exception {
        HttpResponse<String> channel = response(200, """
                {"items":[{"contentDetails":{"relatedPlaylists":{"uploads":"UUfixture"}}}]}
                """);
        HttpResponse<String> playlist = response(200, """
                {"items":[
                  {"contentDetails":{"videoId":"video-1"}},
                  {"contentDetails":{"videoId":"video-2"}}
                ],"nextPageToken":"next-page"}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(channel, playlist);

        YoutubeChannelVideoQueryPort.VideoPage page = adapter().query("channel-1", null);

        assertThat(page.videoIds()).containsExactly("video-1", "video-2");
        assertThat(page.nextPageToken()).isEqualTo("next-page");
        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, org.mockito.Mockito.times(2)).send(requests.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(requests.getAllValues().get(0).uri().getPath()).isEqualTo("/youtube/v3/channels");
        assertThat(requests.getAllValues().get(0).uri().getQuery()).contains("part=contentDetails", "id=channel-1", "key=test-key");
        assertThat(requests.getAllValues().get(1).uri().getPath()).isEqualTo("/youtube/v3/playlistItems");
        assertThat(requests.getAllValues().get(1).uri().getQuery()).contains("playlistId=UUfixture", "maxResults=50", "key=test-key");
    }

    @Test
    @DisplayName("항목의 영상 ID가 없으면 목록을 조용히 생략하지 않고 실패한다")
    void 채널영상조회_영상ID누락_응답계약오류로실패한다() throws Exception {
        HttpResponse<String> channel = response(200, """
                {"items":[{"contentDetails":{"relatedPlaylists":{"uploads":"UUfixture"}}}]}
                """);
        HttpResponse<String> playlist = response(200, """
                {"items":[{"contentDetails":{}}]}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(channel, playlist);

        assertThatThrownBy(() -> adapter().query("channel-1", null))
                .isInstanceOf(YoutubeChannelVideoQueryException.class)
                .extracting(exception -> ((YoutubeChannelVideoQueryException) exception).category())
                .isEqualTo("YOUTUBE_MALFORMED_RESPONSE");
    }

    @Test
    @DisplayName("429와 timeout은 안정적인 오류 범주로 변환한다")
    void 채널영상조회_제공자429와timeout_오류범주를보존한다() throws Exception {
        HttpResponse<String> rateLimited = response(429, "{}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(rateLimited);

        assertThatThrownBy(() -> adapter().query("channel-1", null))
                .isInstanceOf(YoutubeChannelVideoQueryException.class)
                .extracting(exception -> ((YoutubeChannelVideoQueryException) exception).category())
                .isEqualTo("YOUTUBE_RATE_LIMIT");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("timeout"));
        assertThatThrownBy(() -> adapter().query("channel-1", null))
                .isInstanceOf(YoutubeChannelVideoQueryException.class)
                .extracting(exception -> ((YoutubeChannelVideoQueryException) exception).category())
                .isEqualTo("YOUTUBE_TIMEOUT");
    }

    @Test
    @DisplayName("허용되지 않은 YouTube endpoint origin은 호출 전에 거부한다")
    void 초기화_허용되지않은Origin_호출전에거부한다() {
        YoutubeChannelBackfillProperties properties = properties();
        properties.setAllowedOrigins(List.of("https://example.invalid"));

        assertThatThrownBy(() -> new YouTubeChannelVideoQueryAdapter(httpClient, properties))
                .isInstanceOf(IllegalStateException.class);
    }

    private YouTubeChannelVideoQueryAdapter adapter() {
        return new YouTubeChannelVideoQueryAdapter(httpClient, properties());
    }

    private YoutubeChannelBackfillProperties properties() {
        YoutubeChannelBackfillProperties properties = new YoutubeChannelBackfillProperties();
        properties.setApiKey("test-key");
        return properties;
    }

    private HttpResponse<String> response(int status, String body) {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (name, value) -> true));
        return response;
    }
}
