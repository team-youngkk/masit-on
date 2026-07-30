package com.masiton.security.infrastructure.configuration;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.http.HttpServletRequest;

import com.masiton.member.application.MemberAuthenticationStoreUnavailableException;
import com.masiton.member.application.MemberAuthenticationValidationService;
import com.masiton.member.application.MemberPrincipal;
import com.masiton.security.infrastructure.web.SecurityErrorWriter;

@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter,
            @Qualifier("memberJwtAuthenticationConverter")
            Converter<Jwt, ? extends AbstractAuthenticationToken> memberJwtAuthenticationConverter,
            SecurityErrorWriter securityErrorWriter,
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
                                "/api/creators").permitAll()
                        .requestMatchers("/internal/health/live", "/internal/health/ready", "/internal/health/dependencies")
                        .permitAll()
                        .requestMatchers("/api/admin/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/auth/tokens").hasAuthority("MEMBER")
                        .requestMatchers("/api/me", "/api/me/**").hasAuthority("MEMBER")
                        .requestMatchers("/api/**", "/internal/**").denyAll()
                        // Non-API paths are owned by the web application, not by this API security boundary.
                        .anyRequest().permitAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(securityErrorWriter)
                        .authenticationManagerResolver(authenticationManagerResolver(
                                jwtDecoder,
                                memberJwtDecoder,
                                jwtAuthenticationConverter,
                                memberJwtAuthenticationConverter))
                        .addObjectPostProcessor(
                                new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                                    @Override
                                    public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                                        filter.setAuthenticationFailureHandler(securityErrorWriter);
                                        return filter;
                                    }
                                }))
                .build();
    }

    private AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver(
            JwtDecoder adminJwtDecoder,
            JwtDecoder memberJwtDecoder,
            Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter,
            Converter<Jwt, ? extends AbstractAuthenticationToken> memberJwtAuthenticationConverter
    ) {
        AuthenticationManager adminAuthenticationManager = authenticationManager(adminJwtDecoder, jwtAuthenticationConverter);
        AuthenticationManager memberAuthenticationManager =
                authenticationManager(memberJwtDecoder, memberJwtAuthenticationConverter);
        AuthenticationManager optionalMemberAuthenticationManager =
                authentication -> authenticatePublicRequest(authentication, memberAuthenticationManager);
        return request -> {
            String requestUri = request.getRequestURI();
            if (isMemberBoundary(requestUri)) {
                return memberAuthenticationManager;
            }
            return isOptionalMemberDetailRequest(request)
                    ? optionalMemberAuthenticationManager
                    : adminAuthenticationManager;
        };
    }

    private boolean isMemberBoundary(String requestUri) {
        return requestUri.startsWith("/api/auth/")
                || requestUri.equals("/api/me")
                || requestUri.startsWith("/api/me/");
    }

    private boolean isOptionalMemberDetailRequest(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        String requestUri = request.getRequestURI();
        String detailPrefix = "/api/restaurants/";
        if (!requestUri.startsWith(detailPrefix)) {
            return false;
        }
        String restaurantId = requestUri.substring(detailPrefix.length());
        return !restaurantId.isEmpty() && !restaurantId.contains("/");
    }

    private Authentication authenticatePublicRequest(
            Authentication authentication,
            AuthenticationManager memberAuthenticationManager
    ) {
        try {
            return memberAuthenticationManager.authenticate(authentication);
        } catch (AuthenticationException memberAuthenticationFailure) {
            return new AnonymousAuthenticationToken(
                    "public-read", "anonymousUser",
                    AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
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

    @Bean("memberJwtAuthenticationConverter")
    Converter<Jwt, ? extends AbstractAuthenticationToken> memberJwtAuthenticationConverter(
            MemberAuthenticationValidationService memberAuthenticationValidationService,
            Clock memberSessionClock
    ) {
        return jwt -> {
            MemberPrincipal principal = authenticateMember(jwt, memberAuthenticationValidationService, memberSessionClock.instant());
            List<String> roles = jwt.getClaimAsStringList("roles");
            JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                    jwt,
                    (roles == null ? List.<String>of() : roles).stream()
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toUnmodifiableSet()),
                    principal.memberId()
            );
            authentication.setDetails(principal);
            return authentication;
        };
    }

    private MemberPrincipal authenticateMember(
            Jwt jwt,
            MemberAuthenticationValidationService memberAuthenticationValidationService,
            Instant now
    ) {
        try {
            return memberAuthenticationValidationService
                    .validate(jwt.getSubject(), jwt.getClaimAsString("sid"), now)
                    .orElseThrow(() -> new BadCredentialsException("Member token is invalid"));
        } catch (MemberAuthenticationStoreUnavailableException exception) {
            throw new AuthenticationServiceException("Member authentication state is unavailable", exception);
        }
    }
}
