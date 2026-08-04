package com.masiton.security.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.masiton.security.infrastructure.configuration.VerificationAccessProperties;

@DisplayName("검증 참여자 요청 출처 해석")
class VerificationClientAddressResolverTest {

    @Test
    @DisplayName("신뢰한 프록시의 단일 전달 주소만 사용한다")
    void resolve_신뢰프록시_단일전달주소사용() {
        MockHttpServletRequest request = request("10.0.0.2", "198.51.100.20");

        assertThat(resolver(true, "10.0.0.2").resolve(request)).isEqualTo("198.51.100.20");
    }

    @Test
    @DisplayName("신뢰하지 않은 peer와 다중 전달 주소는 사용하지 않는다")
    void resolve_신뢰경계밖입력_peer주소사용() {
        assertThat(resolver(true, "10.0.0.2").resolve(request("192.0.2.10", "198.51.100.20")))
                .isEqualTo("192.0.2.10");
        assertThat(resolver(true, "10.0.0.2").resolve(request("10.0.0.2", "198.51.100.20, 10.0.0.1")))
                .isEqualTo("10.0.0.2");
    }

    @Test
    @DisplayName("프록시 모드에 신뢰 주소가 없으면 기동을 거부한다")
    void 설정검증_프록시모드_신뢰주소없음_거부() {
        VerificationAccessProperties properties = new VerificationAccessProperties();
        properties.setReverseProxyEnabled(true);

        assertThatThrownBy(properties::validateProxyBoundary).isInstanceOf(IllegalStateException.class);
    }

    private VerificationClientAddressResolver resolver(boolean enabled, String trustedAddress) {
        VerificationAccessProperties properties = new VerificationAccessProperties();
        properties.setReverseProxyEnabled(enabled);
        properties.setTrustedProxyAddresses(trustedAddress);
        return new VerificationClientAddressResolver(properties);
    }

    private MockHttpServletRequest request(String peer, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(peer);
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }
}
