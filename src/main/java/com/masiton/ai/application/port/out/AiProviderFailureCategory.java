package com.masiton.ai.application.port.out;

/** Failure categories persisted by the worker without exposing provider payloads or secrets. */
public enum AiProviderFailureCategory {
    PROVIDER_BLOCKED,
    TIMEOUT,
    RATE_LIMIT,
    UPSTREAM,
    SCHEMA
}
