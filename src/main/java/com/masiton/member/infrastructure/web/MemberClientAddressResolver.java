package com.masiton.member.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import com.masiton.common.web.ClientAddressResolver;
import com.masiton.common.web.TrustedProxyClientAddressResolver;
import com.masiton.member.infrastructure.configuration.MemberRateLimitProperties;

@Component
public class MemberClientAddressResolver implements ClientAddressResolver {
    private final ClientAddressResolver delegate;

    public MemberClientAddressResolver(MemberRateLimitProperties properties) {
        this.delegate = new TrustedProxyClientAddressResolver(
                properties.isReverseProxyEnabled(), properties.trustedProxyAddresses());
    }

    @Override
    public String resolve(HttpServletRequest request) {
        return delegate.resolve(request);
    }
}
