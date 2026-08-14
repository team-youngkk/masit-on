package com.masiton.security.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import com.masiton.common.web.ClientAddressResolver;
import com.masiton.common.web.TrustedProxyClientAddressResolver;
import com.masiton.security.infrastructure.configuration.SecurityProperties;

@Component
public class AdminClientAddressResolver implements ClientAddressResolver {

    private final ClientAddressResolver delegate;

    public AdminClientAddressResolver(SecurityProperties properties) {
        SecurityProperties.LoginFailure loginFailure = properties.getLoginFailure();
        this.delegate = new TrustedProxyClientAddressResolver(
                loginFailure.isReverseProxyEnabled(), loginFailure.trustedProxyAddresses());
    }

    @Override
    public String resolve(HttpServletRequest request) {
        return delegate.resolve(request);
    }
}
