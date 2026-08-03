package com.masiton.member.infrastructure.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.masiton.member.infrastructure.configuration.MemberRateLimitProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("회원 요청 출처 해석")
class MemberClientAddressResolverTest {

    @Test
    @DisplayName("신뢰하지 않은 peer의 전달 헤더는 무시한다")
    void resolve_신뢰하지않은peer_전달헤더무시() {
        MockHttpServletRequest request = request("192.0.2.10", "198.51.100.20");

        assertThat(resolver("10.0.0.2").resolve(request)).isEqualTo("192.0.2.10");
    }

    @Test
    @DisplayName("신뢰한 프록시 peer의 단일 전달 주소만 사용한다")
    void resolve_신뢰프록시_단일전달주소사용() {
        MockHttpServletRequest request = request("10.0.0.2", "198.51.100.20");

        assertThat(resolver("10.0.0.2").resolve(request)).isEqualTo("198.51.100.20");
    }

    @Test
    @DisplayName("프록시 모드가 꺼져 있으면 신뢰 목록의 peer도 전달 헤더를 사용하지 않는다")
    void resolve_프록시모드꺼짐_전달헤더무시() {
        MockHttpServletRequest request = request("10.0.0.2", "198.51.100.20");

        assertThat(resolver(false, "10.0.0.2").resolve(request)).isEqualTo("10.0.0.2");
    }

    @Test
    @DisplayName("신뢰 프록시라도 다중 전달 주소는 신뢰하지 않는다")
    void resolve_신뢰프록시_다중전달주소무시() {
        MockHttpServletRequest request = request("10.0.0.2", "198.51.100.20, 10.0.0.1");

        assertThat(resolver("10.0.0.2").resolve(request)).isEqualTo("10.0.0.2");
    }

    @Test
    @DisplayName("프록시 모드에서 신뢰 peer가 없으면 기동을 거부한다")
    void 설정검증_프록시모드_신뢰peer없음_거부() {
        MemberRateLimitProperties properties = new MemberRateLimitProperties();
        properties.setSecret("test-secret");
        properties.setReverseProxyEnabled(true);

        assertThatThrownBy(properties::validateProxyBoundary).isInstanceOf(IllegalStateException.class);
    }

    private MemberClientAddressResolver resolver(String trustedProxyAddress) {
        return resolver(true, trustedProxyAddress);
    }

    private MemberClientAddressResolver resolver(boolean reverseProxyEnabled, String trustedProxyAddress) {
        MemberRateLimitProperties properties = new MemberRateLimitProperties();
        properties.setSecret("test-secret");
        properties.setTrustedProxyAddresses(trustedProxyAddress);
        properties.setReverseProxyEnabled(reverseProxyEnabled);
        return new MemberClientAddressResolver(properties);
    }

    private MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }
}
