package com.masiton.ai.application.port.out.dto;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AI 영상 추출 Provider 요청")
class AiVideoExtractionRequestTest {

    @Test
    @DisplayName("공개 YouTube HTTPS URL이 아니면 Provider 요청을 만들지 않는다")
    void 생성_공개YouTubeHTTPSURL아님_거부한다() {
        assertThatThrownBy(() -> new AiVideoExtractionRequest(
                URI.create("https://evil.example/video-id"), ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiVideoExtractionRequest(
                URI.create("http://www.youtube.com/watch?v=video-id"), ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiVideoExtractionRequest(
                URI.create("https://www.youtube.com:443/watch?v=video-id"), ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("YouTube 영상 식별자가 있는 HTTPS URL은 정규화된 보조 입력으로 생성한다")
    void 생성_유효한YouTubeURL_보조입력을trim한다() {
        AiVideoExtractionRequest request = new AiVideoExtractionRequest(
                URI.create("https://www.youtube.com/watch?v=video-id"), "  보완 메모  ");

        org.assertj.core.api.Assertions.assertThat(request.supplementText()).isEqualTo("보완 메모");
    }

    @Test
    @DisplayName("중복 영상 쿼리나 과도한 보완 텍스트는 Provider 요청을 만들지 않는다")
    void 생성_중복영상쿼리와보완텍스트초과_거부한다() {
        assertThatThrownBy(() -> new AiVideoExtractionRequest(
                URI.create("https://www.youtube.com/watch?v=video-id&v=another-id"), ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiVideoExtractionRequest(
                URI.create("https://www.youtube.com/watch?v=video-id"), "x".repeat(20_001)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
