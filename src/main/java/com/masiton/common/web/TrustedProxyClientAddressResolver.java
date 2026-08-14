package com.masiton.common.web;

import java.util.Enumeration;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 신뢰된 reverse proxy가 전달한 단일 IP 리터럴만 요청 출처로 사용한다.
 * DNS 조회가 발생하지 않도록 IPv4·IPv6 리터럴 문법을 직접 검증한다.
 */
public final class TrustedProxyClientAddressResolver implements ClientAddressResolver {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String IPV4_PART = "(?:25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)";

    private final boolean reverseProxyEnabled;
    private final Set<String> trustedProxyAddresses;

    public TrustedProxyClientAddressResolver(boolean reverseProxyEnabled, Set<String> trustedProxyAddresses) {
        this.reverseProxyEnabled = reverseProxyEnabled;
        this.trustedProxyAddresses = trustedProxyAddresses;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String peerAddress = request.getRemoteAddr();
        if (!reverseProxyEnabled || !trustedProxyAddresses.contains(peerAddress)) {
            return peerAddress;
        }

        String forwardedFor = singleForwardedFor(request);
        return forwardedFor != null && isSafeIpAddress(forwardedFor) ? forwardedFor : peerAddress;
    }

    private String singleForwardedFor(HttpServletRequest request) {
        Enumeration<String> values = request.getHeaders(FORWARDED_FOR_HEADER);
        if (values == null || !values.hasMoreElements()) {
            return null;
        }

        String value = values.nextElement();
        if (values.hasMoreElements() || value == null || value.isBlank() || value.contains(",")) {
            return null;
        }
        return value.trim();
    }

    private boolean isSafeIpAddress(String value) {
        return value.matches(IPV4_PART + "(?:\\." + IPV4_PART + "){3}")
                || isIpv6Literal(value);
    }

    private boolean isIpv6Literal(String value) {
        if (!value.contains(":") || !value.matches("[0-9A-Fa-f:.]+")) {
            return false;
        }

        String[] sides = value.split("::", -1);
        if (sides.length > 2) {
            return false;
        }

        int leftGroups = ipv6GroupCount(sides[0]);
        if (leftGroups < 0) {
            return false;
        }
        if (sides.length == 1) {
            return leftGroups == 8;
        }

        if (sides[0].contains(".")) {
            return false;
        }

        int rightGroups = ipv6GroupCount(sides[1]);
        return rightGroups >= 0 && leftGroups + rightGroups < 8;
    }

    private int ipv6GroupCount(String side) {
        if (side.isEmpty()) {
            return 0;
        }

        String[] groups = side.split(":", -1);
        int count = 0;
        for (int index = 0; index < groups.length; index++) {
            String group = groups[index];
            if (group.isEmpty()) {
                return -1;
            }
            if (group.contains(".")) {
                if (index != groups.length - 1 || !group.matches(IPV4_PART + "(?:\\." + IPV4_PART + "){3}")) {
                    return -1;
                }
                count += 2;
            } else {
                if (group.length() > 4 || !group.matches("[0-9A-Fa-f]+")) {
                    return -1;
                }
                count++;
            }
        }
        return count;
    }
}
