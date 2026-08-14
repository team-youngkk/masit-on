package com.masiton.video.application;

import java.net.URI;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.video.application.port.in.ResolveVerifiedVideoUseCase;
import com.masiton.video.application.port.out.VerifiedVideo;
import com.masiton.video.application.port.out.VideoVerificationPort;

@Service
public class ResolveVerifiedVideoService implements ResolveVerifiedVideoUseCase {

    private final VideoVerificationPort videoVerificationPort;

    public ResolveVerifiedVideoService(VideoVerificationPort videoVerificationPort) {
        this.videoVerificationPort = videoVerificationPort;
    }

    @Override
    public Optional<VerifiedVideo> resolve(URI sourceUrl) {
        try {
            return videoVerificationPort.verify(sourceUrl);
        } catch (VideoVerificationFailedException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }
}
