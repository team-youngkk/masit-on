package com.masiton.security.infrastructure.web;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import com.masiton.security.infrastructure.configuration.SecurityProperties;

@Component
public class AdminClientAddressResolver {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final boolean reverseProxyEnabled;
    private final Set<String> trustedProxyAddresses;

    public AdminClientAddressResolver(SecurityProperties properties) {
        SecurityProperties.LoginFailure loginFailure = properties.getLoginFailure();
        this.reverseProxyEnabled = loginFailure.isReverseProxyEnabled();
        this.trustedProxyAddresses = loginFailure.trustedProxyAddresses();
    }

    public String resolve(HttpServletRequest request) {
        String peerAddress = request.getRemoteAddr();
        if (!reverseProxyEnabled || !trustedProxyAddresses.contains(peerAddress)) {
            return peerAddress;
        }

        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwardedFor == null || forwardedFor.isBlank() || forwardedFor.contains(",")) {
            return peerAddress;
        }

        String clientAddress = forwardedFor.trim();
        return isSafeIpAddress(clientAddress) ? clientAddress : peerAddress;
    }

    private boolean isSafeIpAddress(String value) {
        if (value.matches("(?:25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)){3}")) {
            return true;
        }
        if (!value.contains(":") || !value.matches("[0-9A-Fa-f:.]+")) {
            return false;
        }
        try {
            InetAddress.getByName(value);
            return true;
        } catch (UnknownHostException exception) {
            return false;
        }
    }
}
