package com.masiton.security.infrastructure.configuration;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("masiton.security")
public class SecurityProperties {

    private final Jwt jwt = new Jwt();
    private Duration refreshTokenTtl = Duration.ofDays(14);
    private String cookieName = "masit_on_refresh";
    private boolean secure = true;
    private String sameSite = "Strict";
    private String path = "/api/admin/auth";
    private final Member member = new Member();
    private final LoginFailure loginFailure = new LoginFailure();

    public Jwt getJwt() {
        return jwt;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public String getSameSite() {
        return sameSite;
    }

    public void setSameSite(String sameSite) {
        this.sameSite = sameSite;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public LoginFailure getLoginFailure() {
        return loginFailure;
    }

    public Member getMember() {
        return member;
    }

    public static class Jwt {

        private String issuer = "masit-on";
        private String audience = "masit-on-admin-api";
        private String memberAudience = "masit-on-member-api";
        private Duration accessTokenTtl = Duration.ofMinutes(30);
        private Duration memberAccessTokenTtl = Duration.ofMinutes(30);
        private String keyId;
        private String privateKeyPem;
        private String publicKeyPem;
        private Map<String, String> verificationKeys = new LinkedHashMap<>();

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }

        public String getMemberAudience() {
            return memberAudience;
        }

        public void setMemberAudience(String memberAudience) {
            this.memberAudience = memberAudience;
        }

        public Duration getAccessTokenTtl() {
            return accessTokenTtl;
        }

        public void setAccessTokenTtl(Duration accessTokenTtl) {
            this.accessTokenTtl = accessTokenTtl;
        }

        public Duration getMemberAccessTokenTtl() {
            return memberAccessTokenTtl;
        }

        public void setMemberAccessTokenTtl(Duration memberAccessTokenTtl) {
            this.memberAccessTokenTtl = memberAccessTokenTtl;
        }

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }

        public String getPrivateKeyPem() {
            return privateKeyPem;
        }

        public void setPrivateKeyPem(String privateKeyPem) {
            this.privateKeyPem = privateKeyPem;
        }

        public String getPublicKeyPem() {
            return publicKeyPem;
        }

        public void setPublicKeyPem(String publicKeyPem) {
            this.publicKeyPem = publicKeyPem;
        }

        public Map<String, String> getVerificationKeys() {
            return verificationKeys;
        }

        public void setVerificationKeys(Map<String, String> verificationKeys) {
            this.verificationKeys = new LinkedHashMap<>(verificationKeys);
        }
    }

    public static class Member {

        private Duration refreshTokenTtl = Duration.ofDays(14);
        private String cookieName = "__Secure-masiton-member-refresh";
        private String path = "/api/auth/tokens";
        private String publicBaseUrl = "http://localhost:3000";
        private int maxSessions = 3;

        public Duration getRefreshTokenTtl() {
            return refreshTokenTtl;
        }

        public void setRefreshTokenTtl(Duration refreshTokenTtl) {
            this.refreshTokenTtl = refreshTokenTtl;
        }

        public String getCookieName() {
            return cookieName;
        }

        public void setCookieName(String cookieName) {
            this.cookieName = cookieName;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getPublicBaseUrl() {
            return publicBaseUrl;
        }

        public void setPublicBaseUrl(String publicBaseUrl) {
            this.publicBaseUrl = publicBaseUrl;
        }

        public int getMaxSessions() {
            return maxSessions;
        }

        public void setMaxSessions(int maxSessions) {
            this.maxSessions = maxSessions;
        }
    }

    public static class LoginFailure {

        private int maxAttempts = 5;
        private Duration ttl = Duration.ofMinutes(15);

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }
    }
}
