package com.masiton.security.infrastructure.web;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** `/api/me` 성공·오류 응답이 공유 캐시에 저장되지 않게 하는 회원 보안 경계다. */
@Component
public class MemberPrivateCacheFilter extends OncePerRequestFilter {

    private static final String MEMBER_PATH = "/api/me";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (isMemberPath(request.getRequestURI())) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        }
        filterChain.doFilter(request, response);
    }

    private boolean isMemberPath(String path) {
        return path.equals(MEMBER_PATH) || path.startsWith(MEMBER_PATH + "/");
    }
}
