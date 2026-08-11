package com.masiton.ai.presentation.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.masiton.ai.application.port.in.AiExtractionJobUseCase;
import com.masiton.common.web.BusinessException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("YouTube 채널 webhook Controller")
class YouTubeChannelWebhookControllerTest {

    private static final String SECRET = "webhook-secret-for-test";
    private static final String PAYLOAD = """
            <feed xmlns="http://www.w3.org/2005/Atom"
                  xmlns:yt="http://www.youtube.com/xml/schemas/2015">
              <yt:channelId>channel-1</yt:channelId>
              <yt:videoId>video-1</yt:videoId>
              <link rel="alternate" href="http://www.youtube.com/watch?v=video-1"/>
            </feed>
            """;

    private final AiExtractionJobUseCase useCase = mock(AiExtractionJobUseCase.class);
    private final YoutubeWebhookProperties properties = properties(SECRET);
    private final YouTubeChannelWebhookController controller = new YouTubeChannelWebhookController(
            new YoutubeAtomNotificationParser(), properties, useCase);

    @Test
    @DisplayName("유효한 SHA-256 서명만 webhook 작업 접수까지 전달한다")
    void receive_유효한서명_작업을전달한다() throws Exception {
        when(useCase.submitWebhook("channel-1", "video-1",
                java.net.URI.create("https://www.youtube.com/watch?v=video-1")))
                .thenReturn(java.util.Optional.empty());

        assertThatCode(() -> controller.receive(request(PAYLOAD), hmac("HmacSHA256", "sha256="), null))
                .doesNotThrowAnyException();

        verify(useCase).submitWebhook("channel-1", "video-1",
                java.net.URI.create("https://www.youtube.com/watch?v=video-1"));
    }

    @Test
    @DisplayName("SHA-1 호환 서명도 유효한 webhook으로 접수한다")
    void receive_SHA1호환서명_작업을전달한다() throws Exception {
        when(useCase.submitWebhook("channel-1", "video-1",
                java.net.URI.create("https://www.youtube.com/watch?v=video-1")))
                .thenReturn(java.util.Optional.empty());

        assertThatCode(() -> controller.receive(request(PAYLOAD), null, hmac("HmacSHA1", "sha1=")))
                .doesNotThrowAnyException();

        verify(useCase).submitWebhook("channel-1", "video-1",
                java.net.URI.create("https://www.youtube.com/watch?v=video-1"));
    }

    @Test
    @DisplayName("YouTube topic의 호스트와 경로가 다르면 구독 확인을 거부한다")
    void verify_잘못된Topic_거부한다() {
        assertThatThrownBy(() -> controller.verify(
                "https://evil.example/xml/feeds/videos.xml?channel_id=channel-1", "token", "challenge"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo("AIEXTRACT_INVALID_WEBHOOK_TOPIC");
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("서명이 없거나 틀리면 XML 파싱과 작업 접수를 수행하지 않는다")
    void receive_서명누락또는불일치_거부한다() {
        assertThatThrownBy(() -> controller.receive(request(PAYLOAD), null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo("AIEXTRACT_WEBHOOK_SIGNATURE_INVALID");
        assertThatThrownBy(() -> controller.receive(request(PAYLOAD), "sha256=00", null))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("비밀키가 주입되지 않으면 fail-closed로 webhook을 거부한다")
    void receive_비밀키누락_거부한다() {
        YouTubeChannelWebhookController controllerWithoutSecret = new YouTubeChannelWebhookController(
                new YoutubeAtomNotificationParser(), properties(""), useCase);

        assertThatThrownBy(() -> controllerWithoutSecret.receive(request(PAYLOAD), "sha256=00", null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo("AIEXTRACT_WEBHOOK_SIGNATURE_INVALID");
        verifyNoInteractions(useCase);
    }

    private MockHttpServletRequest request(String payload) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(payload.getBytes(StandardCharsets.UTF_8));
        request.setContentType("application/atom+xml");
        return request;
    }

    private YoutubeWebhookProperties properties(String secret) {
        YoutubeWebhookProperties result = new YoutubeWebhookProperties();
        result.setSecret(secret);
        return result;
    }

    private String hmac(String algorithm, String prefix) throws Exception {
        Mac mac = Mac.getInstance(algorithm);
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), algorithm));
        byte[] digest = mac.doFinal(PAYLOAD.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(prefix);
        for (byte value : digest) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }
}
