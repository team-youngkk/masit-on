package com.masiton.security.infrastructure.configuration;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import com.masiton.security.application.SecurityTokenLifetime;

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
    JwtEncoder jwtEncoder(SecurityProperties properties) {
        RSAPrivateKey privateKey = JwtKeyParser.privateKey(properties.getJwt().getPrivateKeyPem());
        RSAPublicKey publicKey = JwtKeyParser.publicKey(properties.getJwt().getPublicKeyPem());
        RSAKey jwk = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(properties.getJwt().getKeyId())
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    @Bean
    JwtDecoder jwtDecoder(SecurityProperties properties) {
        RSAPublicKey publicKey = JwtKeyParser.publicKey(properties.getJwt().getPublicKeyPem());
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(properties.getJwt().getAudience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.getJwt().getIssuer()),
                audienceValidator
        ));
        return decoder;
    }
}
