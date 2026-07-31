package com.masiton.creator.infrastructure.external;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.masiton.creator.application.port.out.VerifiedChannel;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * snippet에서 표시 정보(handle·description·profileImageUrl)를 추출하는 정규화 규칙을
 * 검증한다. 세 값은 모두 선택 값이므로 어느 것이 없어도 채널 검증 실패로 넓어지지 않는다.
 */
@DisplayName("YouTube 채널 검증 Adapter")
class YouTubeChannelVerificationAdapterTest {

    private static final URI SUBMITTED_URL = URI.create("https://www.youtube.com/channel/UCfixtureNormalChannel01");

    private final HttpClient httpClient = mock(HttpClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("high 썸네일이 있으면 우선 선택하고 customUrl·description을 그대로 추출한다")
    void 검증_high썸네일존재_high를선택하고표시정보를추출한다() throws Exception {
        givenResponse(200, channel("""
                "customUrl": "@masiton-fixture",
                "description": "채널 소개",
                "thumbnails": {
                  "high": { "url": "https://i.ytimg.com/vi/fixtureVid1/hqdefault.jpg" },
                  "medium": { "url": "https://i.ytimg.com/vi/fixtureVid1/mqdefault.jpg" }
                }
                """));

        Optional<VerifiedChannel> verified = adapter().verify(SUBMITTED_URL);

        assertThat(verified).isPresent();
        assertThat(verified.get().handle()).isEqualTo("@masiton-fixture");
        assertThat(verified.get().description()).isEqualTo("채널 소개");
        assertThat(verified.get().profileImageUrl()).isEqualTo("https://i.ytimg.com/vi/fixtureVid1/hqdefault.jpg");
    }

    @Test
    @DisplayName("high가 없으면 medium을 선택한다")
    void 검증_high없음_medium을선택한다() throws Exception {
        givenResponse(200, channel("""
                "thumbnails": {
                  "medium": { "url": "https://i.ytimg.com/vi/fixtureVid1/mqdefault.jpg" },
                  "default": { "url": "https://i.ytimg.com/vi/fixtureVid1/default.jpg" }
                }
                """));

        Optional<VerifiedChannel> verified = adapter().verify(SUBMITTED_URL);

        assertThat(verified).isPresent();
        assertThat(verified.get().profileImageUrl()).isEqualTo("https://i.ytimg.com/vi/fixtureVid1/mqdefault.jpg");
    }

    @Test
    @DisplayName("high·medium이 없으면 default를 선택한다")
    void 검증_high와medium없음_default를선택한다() throws Exception {
        givenResponse(200, channel("""
                "thumbnails": {
                  "default": { "url": "https://i.ytimg.com/vi/fixtureVid1/default.jpg" }
                }
                """));

        Optional<VerifiedChannel> verified = adapter().verify(SUBMITTED_URL);

        assertThat(verified).isPresent();
        assertThat(verified.get().profileImageUrl()).isEqualTo("https://i.ytimg.com/vi/fixtureVid1/default.jpg");
    }

    @Test
    @DisplayName("썸네일이 전혀 없으면 profileImageUrl은 null이고 검증 실패로 만들지 않는다")
    void 검증_썸네일없음_profileImageUrlnull이고검증은성공한다() throws Exception {
        givenResponse(200, channel("\"description\": \"채널 소개\""));

        Optional<VerifiedChannel> verified = adapter().verify(SUBMITTED_URL);

        assertThat(verified).isPresent();
        assertThat(verified.get().profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("선택된 썸네일이 https가 아니면 다음 우선순위로 넘기지 않고 null로 떨어뜨린다")
    void 검증_high가http_null로떨어뜨린다() throws Exception {
        givenResponse(200, channel("""
                "thumbnails": {
                  "high": { "url": "http://i.ytimg.com/vi/fixtureVid1/hqdefault.jpg" },
                  "medium": { "url": "https://i.ytimg.com/vi/fixtureVid1/mqdefault.jpg" }
                }
                """));

        Optional<VerifiedChannel> verified = adapter().verify(SUBMITTED_URL);

        assertThat(verified).isPresent();
        assertThat(verified.get().profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("customUrl·description이 없어도 채널 검증은 성공하고 null로 채운다")
    void 검증_customUrl과description없음_null이고검증은성공한다() throws Exception {
        givenResponse(200, channel("\"otherField\": \"무시된다\""));

        Optional<VerifiedChannel> verified = adapter().verify(SUBMITTED_URL);

        assertThat(verified).isPresent();
        assertThat(verified.get().handle()).isNull();
        assertThat(verified.get().description()).isNull();
    }

    @Test
    @DisplayName("customUrl·description이 공백만이면 null로 정리한다")
    void 검증_customUrl과description공백_null로정리한다() throws Exception {
        givenResponse(200, channel("\"customUrl\": \"   \", \"description\": \"   \""));

        Optional<VerifiedChannel> verified = adapter().verify(SUBMITTED_URL);

        assertThat(verified).isPresent();
        assertThat(verified.get().handle()).isNull();
        assertThat(verified.get().description()).isNull();
    }

    /*
     * 응답 파싱과 별개로 요청 URL 구성 자체를 고정한다. part·lookup·key 중 하나가 빠지거나 잘못
     * 조립돼도 응답 stub은 그대로 반환되므로 파싱 테스트만으로는 드러나지 않는다.
     */
    @Test
    @DisplayName("채널 URL 조회는 part=snippet과 id lookup, API 키를 담아 요청한다")
    void 검증_채널URL_part와id와키를담아요청한다() throws Exception {
        givenResponse(200, channel("\"description\": \"채널 소개\""));

        adapter().verify(SUBMITTED_URL);

        URI requested = capturedRequestUri();
        assertThat(requested.getPath()).isEqualTo("/youtube/v3/channels");
        assertThat(requested.getQuery()).contains("part=snippet");
        assertThat(requested.getQuery()).contains("id=UCfixtureNormalChannel01");
        assertThat(requested.getQuery()).contains("key=test-key");
        assertThat(requested.getQuery()).doesNotContain("forHandle=");
    }

    @Test
    @DisplayName("handle URL 조회는 id 대신 forHandle lookup으로 요청한다")
    void 검증_handleURL_forHandlelookup으로요청한다() throws Exception {
        givenResponse(200, channel("\"description\": \"채널 소개\""));

        adapter().verify(URI.create("https://www.youtube.com/@masiton-fixture"));

        /* handle 경로의 마지막 세그먼트를 그대로 쓰므로 `@`가 포함된다. getQuery()는 percent-decode된 값이다. */
        URI requested = capturedRequestUri();
        assertThat(requested.getQuery()).contains("forHandle=@masiton-fixture");
        assertThat(requested.getQuery()).doesNotContain("id=");
    }

    private URI capturedRequestUri() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        return captor.getValue().uri();
    }

    private YouTubeChannelVerificationAdapter adapter() {
        return new YouTubeChannelVerificationAdapter(
                httpClient, objectMapper, "https://www.googleapis.com", "test-key");
    }

    private void givenResponse(int statusCode, String body) throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }

    private String channel(String snippetFields) {
        return """
                {
                  "items": [
                    {
                      "id": "UCfixtureNormalChannel01",
                      "snippet": {
                        "title": "맛잇온 테스트 채널",
                        %s
                      }
                    }
                  ]
                }
                """.formatted(snippetFields);
    }
}
