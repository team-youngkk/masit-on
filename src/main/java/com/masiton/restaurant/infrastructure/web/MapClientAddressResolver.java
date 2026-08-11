package com.masiton.restaurant.infrastructure.web;

import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import com.masiton.common.web.ClientAddressResolver;
import com.masiton.restaurant.infrastructure.configuration.MapRateLimitProperties;

@Component
public class MapClientAddressResolver implements ClientAddressResolver {
    private final boolean reverseProxyEnabled;
    private final Set<String> trustedProxyAddresses;

    public MapClientAddressResolver(MapRateLimitProperties properties) {
        this.reverseProxyEnabled = properties.isReverseProxyEnabled();
        this.trustedProxyAddresses = properties.trustedProxyAddresses();
    }

    @Override
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
