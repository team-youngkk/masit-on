package com.masiton.restaurant.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import com.masiton.common.web.ClientAddressResolver;
import com.masiton.common.web.TrustedProxyClientAddressResolver;
import com.masiton.restaurant.infrastructure.configuration.MapRateLimitProperties;

@Component
public class MapClientAddressResolver implements ClientAddressResolver {
    private final ClientAddressResolver delegate;

    public MapClientAddressResolver(MapRateLimitProperties properties) {
        this.delegate = new TrustedProxyClientAddressResolver(
                properties.isReverseProxyEnabled(), properties.trustedProxyAddresses());
    }

    @Override
    public String resolve(HttpServletRequest request) {
        return delegate.resolve(request);
    }
}
