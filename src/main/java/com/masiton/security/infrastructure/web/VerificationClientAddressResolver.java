package com.masiton.security.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import com.masiton.common.web.ClientAddressResolver;
import com.masiton.common.web.TrustedProxyClientAddressResolver;
import com.masiton.security.infrastructure.configuration.VerificationAccessProperties;

@Component
public class VerificationClientAddressResolver implements ClientAddressResolver {

    private final ClientAddressResolver delegate;

    public VerificationClientAddressResolver(VerificationAccessProperties properties) {
        this.delegate = new TrustedProxyClientAddressResolver(
                properties.isReverseProxyEnabled(), properties.trustedProxyAddresses());
    }

    @Override
    public String resolve(HttpServletRequest request) {
        return delegate.resolve(request);
    }
}
