package com.masiton.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 요청 제한 출처로 사용할 클라이언트 주소를 해석한다.
 * Presentation이 Infrastructure 구현체에 의존하지 않도록 공통 웹 경계에 둔다
 * (docs/06-architecture/dependency-rules.md 2절).
 */
public interface ClientAddressResolver {

    String resolve(HttpServletRequest request);
}
