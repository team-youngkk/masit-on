package com.masiton.video.infrastructure.external;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.masiton.video.application.port.out.VerifiedVideo;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("YouTube 영상 검증 Adapter")
class YouTubeVideoVerificationAdapterTest {

    private static final String VIDEO_ID = "fixtureVid1";
    private final HttpClient httpClient = mock(HttpClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("영상 검증은 videos GET path·part·id·key query 계약을 사용한다")
    void 검증_요청규격_YouTubeVideos계약을따른다() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"items":[{"id":"fixtureVid1","snippet":{
                  "title":"테스트 영상","channelId":"UCfixture","channelTitle":"테스트 채널",
                  "publishedAt":"2026-01-01T00:00:00Z",
                  "thumbnails":{"default":{"url":"https://i.ytimg.com/fixture.jpg"}}
                }}]}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        Optional<VerifiedVideo> verified = adapter().verify(URI.create("https://www.youtube.com/watch?v=" + VIDEO_ID));

        assertThat(verified).isPresent();
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = captor.getValue();
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.uri().getPath()).isEqualTo("/youtube/v3/videos");
        assertThat(request.uri().getQuery()).contains("part=snippet", "id=fixtureVid1", "key=test-key");
    }

    @Test
    @DisplayName("endpoint가 없거나 HTTP(S) origin이 아니면 HTTP 호출 전에 초기화를 거부한다")
    void 초기화_endpoint누락또는지원하지않는형식_호출전에거부한다() {
        assertThatThrownBy(() -> new YouTubeVideoVerificationAdapter(
                httpClient, objectMapper, "", "test-key"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new YouTubeVideoVerificationAdapter(
                httpClient, objectMapper, "ftp://www.googleapis.com", "test-key"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new YouTubeVideoVerificationAdapter(
                httpClient, objectMapper, "http://localhost:8081/youtube", "test-key"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new YouTubeVideoVerificationAdapter(
                httpClient, objectMapper, "http://localhost:8081", "test-key", "https://www.googleapis.com"))
                .isInstanceOf(IllegalStateException.class);
    }

    private YouTubeVideoVerificationAdapter adapter() {
        return new YouTubeVideoVerificationAdapter(httpClient, objectMapper, "https://www.googleapis.com", "test-key");
    }
}
