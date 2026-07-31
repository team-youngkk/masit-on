package com.masiton.video.application.port.out;

import java.time.OffsetDateTime;

public record VerifiedVideo(String externalVideoId, String publisherExternalChannelId, String title, String thumbnailUrl,
                            String channelName, String sourceUrl, OffsetDateTime publishedAt, OffsetDateTime checkedAt) { }
