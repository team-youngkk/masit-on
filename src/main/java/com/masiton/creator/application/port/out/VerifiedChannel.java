package com.masiton.creator.application.port.out;

import java.time.OffsetDateTime;

public record VerifiedChannel(
        String externalChannelId,
        String channelName,
        String channelUrl,
        String profileImageUrl,
        String description,
        String handle,
        OffsetDateTime checkedAt) {
}
