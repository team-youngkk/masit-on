package com.masiton.security.infrastructure.web;

import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import com.masiton.security.infrastructure.configuration.VerificationAccessProperties;

@Component
public class VerificationClientAddressResolver {

    private final boolean reverseProxyEnabled;
    private final Set<String> trustedProxyAddresses;

    public VerificationClientAddressResolver(VerificationAccessProperties properties) {
        this.reverseProxyEnabled = properties.isReverseProxyEnabled();
        this.trustedProxyAddresses = properties.trustedProxyAddresses();
    }

    public String resolve(HttpServletRequest request) {
        String peerAddress = request.getRemoteAddr();
        if (!reverseProxyEnabled || !trustedProxyAddresses.contains(peerAddress)) {
            return peerAddress;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank() || forwardedFor.contains(",")) {
            return peerAddress;
        }
        return forwardedFor.trim();
    }
}
