package com.masiton.creator.application.port.out;

import java.net.URI;
import java.util.Optional;

public interface ChannelVerificationPort {
    Optional<VerifiedChannel> verify(URI channelUrl);
}
