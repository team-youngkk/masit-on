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

    @Test
    @DisplayName("Origin은 스킴·호스트를 소문자로 바꾸고 기본 포트를 제거한다")
    void origin_대소문자와기본포트_정규화() {
        assertThat(OriginCanonicalizer.canonicalize("HTTPS://EXAMPLE.TEST:443"))
                .isEqualTo("https://example.test");
    }

    @Test
    @DisplayName("동등한 canonical Origin은 서로 일치한다")
    void origin_동등한canonicalOrigin_일치() {
        assertThat(OriginCanonicalizer.matches("https://example.test", "HTTPS://EXAMPLE.TEST:443"))
                .isTrue();
    }

    @Test
    @DisplayName("경로가 포함된 값은 Origin으로 인정하지 않는다")
    void origin_경로포함_불일치() {
        assertThat(OriginCanonicalizer.matches("https://example.test/path", "https://example.test"))
                .isFalse();
    }

    @Test
    @DisplayName("비표준 포트는 포트가 다른 Origin과 일치하지 않는다")
    void origin_비표준포트_다른포트와불일치() {
        assertThat(OriginCanonicalizer.matches("https://example.test:8443", "https://example.test"))
                .isFalse();
        assertThat(OriginCanonicalizer.matches("https://example.test/", "https://example.test"))
                .isTrue();
    }
}
