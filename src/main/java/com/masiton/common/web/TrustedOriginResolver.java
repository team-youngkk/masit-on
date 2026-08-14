package com.masiton.common.web;

import java.util.Enumeration;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

public final class TrustedOriginResolver {

    private TrustedOriginResolver() {
    }

    public static Optional<String> resolveSingleOrigin(HttpServletRequest request) {
        Enumeration<String> origins = request.getHeaders(HttpHeaders.ORIGIN);
        if (origins == null || !origins.hasMoreElements()) {
            return Optional.empty();
        }

        String origin = origins.nextElement();
        if (origins.hasMoreElements()) {
            return Optional.empty();
        }
        return Optional.ofNullable(origin);
    }
}
