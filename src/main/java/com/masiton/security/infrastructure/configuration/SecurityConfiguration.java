package com.masiton.security.infrastructure.configuration;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.authentication.AuthenticationManagerResolver;

import jakarta.servlet.http.HttpServletRequest;

import com.masiton.security.infrastructure.RestaurantPathClassifier;
import com.masiton.security.infrastructure.web.AdminCookieOriginFilter;
import com.masiton.security.infrastructure.web.SecurityErrorWriter;
import com.masiton.security.infrastructure.web.MemberSessionRevocationFilter;

@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter,
            SecurityErrorWriter securityErrorWriter,
            AdminCookieOriginFilter adminCookieOriginFilter,
            MemberSessionRevocationFilter memberSessionRevocationFilter,
            JwtDecoder jwtDecoder,
            @Qualifier("memberJwtDecoder") JwtDecoder memberJwtDecoder
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityErrorWriter)
                        .accessDeniedHandler(securityErrorWriter))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/admin/auth/tokens").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/admin/auth/tokens/refresh").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/webhooks/youtube/channel-updates").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/webhooks/youtube/channel-updates").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/verification/sessions").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/verification/sessions").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/registrations",
                                "/api/auth/email-verifications",
                                "/api/auth/email-verifications/resend",
                                "/api/auth/password-resets/requests",
                                "/api/auth/password-resets/confirmations",
                                "/api/auth/tokens",
                                "/api/auth/tokens/refresh").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/restaurants",
                                "/api/restaurants/*",
                                "/api/curations",
                                "/api/curations/*",
                                "/api/creators",
                                "/api/creators/*",
                                "/api/creators/*/restaurants",
                                "/api/creators/*/videos").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/restaurants/course-routes",
                                "/api/restaurants/natural-language-search").permitAll()
                        .requestMatchers("/internal/health/live", "/internal/health/ready", "/internal/health/dependencies")
                        .permitAll()
                        .requestMatchers("/internal/verification/session",
                                "/internal/verification/access-required",
                                "/internal/verification/unavailable").permitAll()
                        .requestMatchers("/api/admin/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/auth/tokens").hasAuthority("MEMBER")
                        .requestMatchers("/api/me", "/api/me/**").hasAuthority("MEMBER")
                        .requestMatchers("/api/**", "/internal/**").denyAll()
                        // Non-API paths are owned by the web application, not by this API security boundary.
                        .anyRequest().permitAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(securityErrorWriter)
                        .bearerTokenResolver(optionalMemberBearerTokenResolver())
                        .authenticationManagerResolver(authenticationManagerResolver(
                                jwtDecoder,
                                memberJwtDecoder,
                                jwtAuthenticationConverter)))
                .addFilterBefore(adminCookieOriginFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(memberSessionRevocationFilter, BearerTokenAuthenticationFilter.class)
                .build();
    }

    private AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver(
            JwtDecoder adminJwtDecoder,
            JwtDecoder memberJwtDecoder,
            Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter
    ) {
        AuthenticationManager adminAuthenticationManager = authenticationManager(adminJwtDecoder, jwtAuthenticationConverter);
        AuthenticationManager memberAuthenticationManager = authenticationManager(memberJwtDecoder, jwtAuthenticationConverter);
        AuthenticationManager publicAuthenticationManager = authentication -> authenticatePublicRequest(
                authentication,
                memberAuthenticationManager
        );
        return request -> {
            String requestUri = request.getRequestURI();
            if (isMemberBoundary(requestUri)) {
                return memberAuthenticationManager;
            }
            return isOptionalMemberAuthenticationRequest(request)
                    ? publicAuthenticationManager
                    : adminAuthenticationManager;
        };
    }

    private BearerTokenResolver optionalMemberBearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> isAnonymousPublicReadRequest(request)
                || isUnauthenticatedAuthenticationRequest(request)
                || isAnonymousPublicWriteRequest(request)
                ? null
                : delegate.resolve(request);
    }

    private boolean isMemberBoundary(String requestUri) {
        return requestUri.startsWith("/api/auth/")
                || requestUri.equals("/api/me")
                || requestUri.startsWith("/api/me/");
    }

    private boolean isOptionalMemberAuthenticationRequest(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        return RestaurantPathClassifier.isRestaurantDetailPath(request.getRequestURI());
    }

    private boolean isAnonymousPublicReadRequest(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        String requestUri = request.getRequestURI();
        return requestUri.equals("/api/restaurants")
                || RestaurantPathClassifier.isNonIdentifierPublicPath(requestUri)
                || isCurationPublicReadRequest(requestUri)
                || requestUri.equals("/api/creators")
                || isCreatorDetailReadRequest(requestUri);
    }

    private boolean isCurationPublicReadRequest(String requestUri) {
        String detailPrefix = "/api/curations/";
        if (requestUri.equals("/api/curations")) {
            return true;
        }
        if (!requestUri.startsWith(detailPrefix)) {
            return false;
        }
        String curationId = requestUri.substring(detailPrefix.length());
        return !curationId.isEmpty() && !curationId.contains("/");
    }

    /**
     * API-CREATOR-DETAIL-001~003은 회원 문맥이 없는 완전 공개 조회다. 맛집 상세와 달리 회원별
     * 부수효과가 없으므로 Bearer Token을 해석하지 않는다. 만료·다른 audience Token을 들고 온
     * 요청도 401 대신 공개 응답을 받는다.
     */
    private boolean isCreatorDetailReadRequest(String requestUri) {
        String detailPrefix = "/api/creators/";
        if (!requestUri.startsWith(detailPrefix)) {
            return false;
        }
        String remainder = requestUri.substring(detailPrefix.length());
        int separatorIndex = remainder.indexOf('/');
        if (separatorIndex < 0) {
            return !remainder.isEmpty();
        }
        String creatorId = remainder.substring(0, separatorIndex);
        String subResource = remainder.substring(separatorIndex + 1);
        return !creatorId.isEmpty() && ("restaurants".equals(subResource) || "videos".equals(subResource));
    }

    /**
     * API-DISCOVERY-COURSE-001은 회원 문맥이 없는 완전 공개 POST 조회다. 회원별 부수효과가 없으므로
     * Bearer Token을 해석하지 않는다. 만료·다른 audience Token을 들고 온 요청도 401 대신 공개 응답을 받는다.
     * 근거: docs/05-specs/api/discovery/restaurant-course-recommendation-api.md 2절
     */
    private boolean isAnonymousPublicWriteRequest(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod())
                && "/api/restaurants/course-routes".equals(request.getRequestURI());
    }

    private boolean isUnauthenticatedAuthenticationRequest(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return false;
        }
        String requestUri = request.getRequestURI();
        return requestUri.equals("/api/admin/auth/tokens")
                || requestUri.equals("/api/admin/auth/tokens/refresh")
                || requestUri.equals("/api/auth/registrations")
                || requestUri.equals("/api/auth/email-verifications")
                || requestUri.equals("/api/auth/email-verifications/resend")
                || requestUri.equals("/api/auth/password-resets/requests")
                || requestUri.equals("/api/auth/password-resets/confirmations")
                || requestUri.equals("/api/auth/tokens")
                || requestUri.equals("/api/auth/tokens/refresh")
                || requestUri.equals("/api/restaurants/natural-language-search");
    }

    private Authentication authenticatePublicRequest(
            Authentication authentication,
            AuthenticationManager memberAuthenticationManager
    ) {
        try {
            return memberAuthenticationManager.authenticate(authentication);
        } catch (AuthenticationException ignored) {
            return null;
        }
    }

    private AuthenticationManager authenticationManager(
            JwtDecoder jwtDecoder,
            Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter
    ) {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwtDecoder);
        provider.setJwtAuthenticationConverter(jwtAuthenticationConverter);
        return provider::authenticate;
    }

    @Bean
    Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            return new JwtAuthenticationToken(
                    jwt,
                    (roles == null ? List.<String>of() : roles).stream()
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toUnmodifiableSet()),
                    jwt.getSubject()
            );
        };
    }
}
