package com.masiton.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

class TrustedOriginResolverTest {

    @Test
    @DisplayName("Origin 헤더가 하나면 그 값을 반환한다")
    void origin_단일헤더_값반환() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.ORIGIN, "https://example.test");

        assertThat(TrustedOriginResolver.resolveSingleOrigin(request))
                .contains("https://example.test");
    }

    @Test
    @DisplayName("Origin 헤더가 없으면 빈 결과를 반환한다")
    void origin_헤더누락_빈결과() {
        assertThat(TrustedOriginResolver.resolveSingleOrigin(new MockHttpServletRequest()))
                .isEmpty();
    }

    @Test
    @DisplayName("Origin 헤더가 여러 개면 빈 결과를 반환한다")
    void origin_다중헤더_빈결과() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.ORIGIN, "https://example.test");
        request.addHeader(HttpHeaders.ORIGIN, "https://evil.test");

        assertThat(TrustedOriginResolver.resolveSingleOrigin(request))
                .isEmpty();
    }
}
