package com.masiton.video.application.port.in;

import java.net.URI;
import java.util.Optional;

import com.masiton.video.application.port.out.VerifiedVideo;

public interface ResolveVerifiedVideoUseCase {
    Optional<VerifiedVideo> resolve(URI sourceUrl);
}
