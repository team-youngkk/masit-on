package com.masiton.security.application.port.out;

import java.time.Duration;

public interface VerificationSessionSettings {
    Duration sessionTtl();
    Duration failureTtl();
}
