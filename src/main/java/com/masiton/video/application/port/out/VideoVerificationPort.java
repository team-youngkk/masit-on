package com.masiton.video.application.port.out;

import java.net.URI;
import java.util.Optional;

public interface VideoVerificationPort {
    Optional<VerifiedVideo> verify(URI sourceUrl);
}
