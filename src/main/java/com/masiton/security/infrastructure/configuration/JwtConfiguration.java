package com.masiton.security.infrastructure.configuration;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Duration;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.masiton.security.application.SecurityTokenLifetime;
import com.masiton.common.security.MemberCookieSettings;
import com.masiton.common.security.MemberJwtSettings;
import com.masiton.common.security.MemberSessionSettings;

@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class JwtConfiguration {

    @Bean
    SecurityTokenLifetime securityTokenLifetime(SecurityProperties properties) {
        return new SecurityTokenLifetime(
                properties.getJwt().getAccessTokenTtl(),
                properties.getRefreshTokenTtl()
        );
    }

    @Bean
    MemberJwtSettings memberJwtSettings(SecurityProperties properties) {
        return new MemberJwtSettings(
                properties.getJwt().getIssuer(),
                properties.getJwt().getAudience(),
                properties.getJwt().getAccessTokenTtl(),
                properties.getJwt().getKeyId()
        );
    }

    @Bean
    MemberSessionSettings memberSessionSettings(SecurityProperties properties) {
        return new MemberSessionSettings(properties.getMember().getMaxSessions());
    }

    @Bean
    MemberCookieSettings memberCookieSettings(SecurityProperties properties, Environment environment) {
        SecurityProperties.Member member = properties.getMember();
        MemberCookieSettings settings = new MemberCookieSettings(
                member.getCookieName(),
                member.getRefreshTokenTtl(),
                member.getPath(),
                properties.isSecure(),
                properties.getSameSite(),
                member.getPublicBaseUrl()
        );
        if (environment.acceptsProfiles(Profiles.of("prod"))
                && settings.allowedOrigins().stream().anyMatch(origin -> !origin.startsWith("https://"))) {
            throw new IllegalStateException("Production authentication origins must use HTTPS");
        }
        return settings;
    }

    @Bean
    JwtEncoder jwtEncoder(SecurityProperties properties) {
        RSAPrivateKey privateKey = JwtKeyParser.privateKey(properties.getJwt().getPrivateKeyPem());
        RSAPublicKey publicKey = JwtKeyParser.publicKey(properties.getJwt().getPublicKeyPem());
        RSAKey jwk = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(requiredKeyId(properties))
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    @Bean
    @Primary
    JwtDecoder jwtDecoder(SecurityProperties properties) {
        return jwtDecoder(properties, properties.getJwt().getAudience());
    }

    @Bean("memberJwtDecoder")
    JwtDecoder memberJwtDecoder(SecurityProperties properties) {
        return jwtDecoder(properties, properties.getJwt().getAudience());
    }

    private JwtDecoder jwtDecoder(SecurityProperties properties, String expectedAudience) {
        Map<String, String> configuredKeys = new LinkedHashMap<>(properties.getJwt().getVerificationKeys());
        configuredKeys.putIfAbsent(requiredKeyId(properties), properties.getJwt().getPublicKeyPem());
        JWKSet keySet = new JWKSet(configuredKeys.entrySet().stream()
                .map(entry -> new RSAKey.Builder(JwtKeyParser.publicKey(entry.getValue()))
                        .keyID(requireKeyId(entry.getKey()))
                        .build())
                .map(com.nimbusds.jose.jwk.JWK.class::cast)
                .toList());
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(
                JWSAlgorithm.RS256,
                new ImmutableJWKSet<>(keySet)
        ));
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(expectedAudience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        OAuth2TokenValidator<Jwt> claimValidator = jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            boolean valid = nonBlank(jwt.getSubject())
                    && nonBlank(jwt.getClaimAsString("sid"))
                    && nonBlank(jwt.getId())
                    && jwt.getIssuedAt() != null && jwt.getExpiresAt() != null
                    && !jwt.getExpiresAt().isBefore(jwt.getIssuedAt())
                    && !Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).minusMinutes(30).isPositive()
                    && roles != null && roles.size() == 1
                    && ("MEMBER".equals(roles.getFirst()) || "ADMIN".equals(roles.getFirst()));
            return valid ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid required claims", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.getJwt().getIssuer()),
                audienceValidator,
                claimValidator
        ));
        return token -> {
            Jwt jwt = decoder.decode(token);
            Object kid = jwt.getHeaders().get("kid");
            if (!(kid instanceof String keyId) || !configuredKeys.containsKey(keyId)) {
                throw new JwtValidationException("JWT kid is missing or unknown", List.of(
                        new OAuth2Error("invalid_token", "JWT kid is missing or unknown", null)
                ));
            }
            return jwt;
        };
    }

    private boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String requiredKeyId(SecurityProperties properties) {
        return requireKeyId(properties.getJwt().getKeyId());
    }

    private String requireKeyId(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalStateException("JWT key id must be configured");
        }
        return keyId;
    }
}
