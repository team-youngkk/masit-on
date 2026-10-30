package com.masiton.ai.application.port.out;

/** Failure categories persisted by the worker without exposing provider payloads or secrets. */
public enum AiProviderFailureCategory {
    PROVIDER_BLOCKED,
    TIMEOUT,
    RATE_LIMIT,
    UPSTREAM,
    SCHEMA,
    AUTHENTICATION,
    PAYMENT_REQUIRED,
    REQUEST_REJECTED,
    RESOURCE_NOT_FOUND,
    SERVER_ERROR,
    NETWORK_ERROR
}
