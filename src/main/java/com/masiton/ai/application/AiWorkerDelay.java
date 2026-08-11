package com.masiton.ai.application;

import java.time.Duration;

@FunctionalInterface
public interface AiWorkerDelay {
    boolean await(Duration duration);
}
