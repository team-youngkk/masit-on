package com.masiton.common.web;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("신뢰된 프록시의 클라이언트 주소 해석")
class TrustedProxyClientAddressResolverTest {

    private final TrustedProxyClientAddressResolver resolver =
            new TrustedProxyClientAddressResolver(true, Set.of("10.0.0.2"));

    @Test
    @DisplayName("유효한 IPv4와 IPv6 리터럴은 출처로 사용한다")
    void resolve_유효한IP리터럴_출처로사용() {
        assertThat(resolver.resolve(request("198.51.100.20"))).isEqualTo("198.51.100.20");
        assertThat(resolver.resolve(request("2001:db8::1"))).isEqualTo("2001:db8::1");
    }

    @Test
    @DisplayName("유효하지 않은 IPv6처럼 보이는 값은 DNS 조회 없이 peer 주소로 대체한다")
    void resolve_유효하지않은IPv6_피어주소사용() {
        assertThat(resolver.resolve(request("1:2:3:4:5:6:7:8:9"))).isEqualTo("10.0.0.2");
        assertThat(resolver.resolve(request("192.0.2.1::"))).isEqualTo("10.0.0.2");
        assertThat(resolver.resolve(request("192.0.2.1::1"))).isEqualTo("10.0.0.2");
    }

    @Test
    @DisplayName("IPv4가 포함된 유효한 IPv6 리터럴은 출처로 사용한다")
    void resolve_유효한IPv4EmbeddedIPv6_출처로사용() {
        assertThat(resolver.resolve(request("::ffff:192.0.2.1"))).isEqualTo("::ffff:192.0.2.1");
        assertThat(resolver.resolve(request("2001:db8::192.0.2.1"))).isEqualTo("2001:db8::192.0.2.1");
    }

    @Test
    @DisplayName("동일 헤더가 여러 줄이면 첫 번째 값도 신뢰하지 않는다")
    void resolve_다중헤더_피어주소사용() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-For", "198.51.100.20");
        request.addHeader("X-Forwarded-For", "198.51.100.21");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.2");
    }

    private MockHttpServletRequest request(String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }
}
