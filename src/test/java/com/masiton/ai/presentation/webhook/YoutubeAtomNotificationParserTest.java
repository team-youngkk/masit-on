package com.masiton.ai.presentation.webhook;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.common.web.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("YouTube Atom webhook parser")
class YoutubeAtomNotificationParserTest {

    private final YoutubeAtomNotificationParser parser = new YoutubeAtomNotificationParser();

    @Test
    @DisplayName("Atom namespace에서 채널, 영상, 공개 URL만 추출한다")
    void parse_정상Atom_식별자를추출한다() {
        YoutubeAtomNotificationParser.YoutubeAtomNotification notification = parser.parse(atomPayload().getBytes(StandardCharsets.UTF_8));

        assertThat(notification.channelId()).isEqualTo("channel-1");
        assertThat(notification.videoId()).isEqualTo("video-1");
        assertThat(notification.videoUrl()).isEqualTo("https://www.youtube.com/watch?v=video-1");
    }

    @Test
    @DisplayName("DOCTYPE과 외부 entity를 포함한 XML은 거부한다")
    void parse_XXE포함XML_400으로거부한다() {
        String payload = """
                <!DOCTYPE feed [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>
                <feed xmlns=\"http://www.w3.org/2005/Atom\"
                      xmlns:yt=\"http://www.youtube.com/xml/schemas/2015\">
                  <yt:channelId>&xxe;</yt:channelId>
                  <yt:videoId>video-1</yt:videoId>
                  <link rel=\"alternate\" href=\"https://www.youtube.com/watch?v=video-1\"/>
                </feed>
                """;

        assertThatThrownBy(() -> parser.parse(payload.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo("AIEXTRACT_INVALID_WEBHOOK_PAYLOAD");
    }

    private String atomPayload() {
        return """
                <feed xmlns=\"http://www.w3.org/2005/Atom\"
                      xmlns:yt=\"http://www.youtube.com/xml/schemas/2015\">
                  <yt:channelId>channel-1</yt:channelId>
                  <yt:videoId>video-1</yt:videoId>
                  <link rel=\"alternate\" href=\"https://www.youtube.com/watch?v=video-1\"/>
                </feed>
                """;
    }
}
