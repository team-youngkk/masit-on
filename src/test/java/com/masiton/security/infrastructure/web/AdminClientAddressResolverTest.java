package com.masiton.security.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.masiton.security.infrastructure.configuration.SecurityProperties;

@DisplayName("관리자 로그인 요청 출처 해석")
class AdminClientAddressResolverTest {

    @Test
    @DisplayName("신뢰한 프록시의 단일 전달 주소만 trim해 사용한다")
    void resolve_신뢰프록시_단일전달주소사용() {
        assertThat(resolver(true, "10.0.0.2").resolve(request("10.0.0.2", " 198.51.100.20 ")))
                .isEqualTo("198.51.100.20");
    }

    @Test
    @DisplayName("프록시 모드가 아니거나 신뢰하지 않은 peer의 전달 헤더는 무시한다")
    void resolve_신뢰경계밖입력_peer주소사용() {
        assertThat(resolver(false, "10.0.0.2").resolve(request("10.0.0.2", "198.51.100.20")))
                .isEqualTo("10.0.0.2");
        assertThat(resolver(true, "10.0.0.2").resolve(request("192.0.2.10", "198.51.100.20")))
                .isEqualTo("192.0.2.10");
    }

    @Test
    @DisplayName("빈 값과 다중 값과 안전하지 않은 전달 주소는 사용하지 않는다")
    void resolve_안전하지않은전달주소_peer주소사용() {
        AdminClientAddressResolver resolver = resolver(true, "10.0.0.2");

        assertThat(resolver.resolve(request("10.0.0.2", null))).isEqualTo("10.0.0.2");
        MockHttpServletRequest forwardedOnlyRequest = request("10.0.0.2", null);
        forwardedOnlyRequest.addHeader("Forwarded", "for=198.51.100.20");
        assertThat(resolver.resolve(forwardedOnlyRequest)).isEqualTo("10.0.0.2");
        assertThat(resolver.resolve(request("10.0.0.2", "   "))).isEqualTo("10.0.0.2");
        assertThat(resolver.resolve(request("10.0.0.2", "198.51.100.20, 10.0.0.1"))).isEqualTo("10.0.0.2");
        assertThat(resolver.resolve(request("10.0.0.2", "client.example.com"))).isEqualTo("10.0.0.2");
    }

    @Test
    @DisplayName("프록시 모드에 신뢰 주소가 없으면 기동을 거부한다")
    void 설정검증_프록시모드_신뢰주소없음_거부() {
        SecurityProperties properties = new SecurityProperties();
        properties.getLoginFailure().setReverseProxyEnabled(true);

        assertThatThrownBy(properties::validateLoginFailureProxyBoundary).isInstanceOf(IllegalStateException.class);
    }

    private AdminClientAddressResolver resolver(boolean enabled, String trustedAddress) {
        SecurityProperties properties = new SecurityProperties();
        properties.getLoginFailure().setReverseProxyEnabled(enabled);
        properties.getLoginFailure().setTrustedProxyAddresses(trustedAddress);
        return new AdminClientAddressResolver(properties);
    }

    private MockHttpServletRequest request(String peer, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(peer);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }
}
