package com.masiton.creator.application.port.out;

import java.time.OffsetDateTime;

public record VerifiedChannel(
        String externalChannelId,
        String channelName,
        String channelUrl,
        OffsetDateTime checkedAt) {
}
