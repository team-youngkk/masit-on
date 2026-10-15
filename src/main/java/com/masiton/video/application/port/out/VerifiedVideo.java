package com.masiton.video.application.port.out;

import java.time.OffsetDateTime;

public record VerifiedVideo(String externalVideoId, String publisherExternalChannelId, String title, String description, String thumbnailUrl,
                            String channelName, String sourceUrl, OffsetDateTime publishedAt, OffsetDateTime checkedAt) {
    public VerifiedVideo(String externalVideoId, String publisherExternalChannelId, String title, String thumbnailUrl,
                         String channelName, String sourceUrl, OffsetDateTime publishedAt, OffsetDateTime checkedAt) {
        this(externalVideoId, publisherExternalChannelId, title, null, thumbnailUrl, channelName, sourceUrl,
                publishedAt, checkedAt);
    }
}
