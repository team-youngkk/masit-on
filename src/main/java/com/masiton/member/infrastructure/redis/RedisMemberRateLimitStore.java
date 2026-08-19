package com.masiton.member.infrastructure.redis;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.masiton.member.application.port.out.MemberRateLimitStore;
import com.masiton.member.infrastructure.configuration.MemberRateLimitProperties;
import com.masiton.common.security.LoginSourceRateLimiter;

@Component
public class RedisMemberRateLimitStore implements MemberRateLimitStore, LoginSourceRateLimiter {

    private static final String PREFIX = "auth:member:rate-limit:";
    private static final String EMAIL_COOLDOWN_PREFIX = PREFIX + "email-cooldown:";
    private static final String EMAIL_DAILY_PREFIX = PREFIX + "email-daily:";
    private static final String ACCOUNT_ACTION_SOURCE_PREFIX = PREFIX + "account-action-source:";
    private static final String EMAIL_VERIFICATION_SOURCE_PREFIX = PREFIX + "email-verification-source:";
    private static final String LOGIN_EMAIL_SOURCE_PREFIX = PREFIX + "login-email-source:";
    private static final String LOGIN_EMAIL_PREFIX = PREFIX + "login-email:";
    private static final String LOGIN_SOURCE_PREFIX = PREFIX + "login-source:";

    private static final Duration EMAIL_COOLDOWN_TTL = Duration.ofSeconds(60);
    private static final Duration EMAIL_DAILY_TTL = Duration.ofDays(1);
    private static final Duration ACCOUNT_ACTION_SOURCE_TTL = Duration.ofHours(1);
    private static final Duration EMAIL_VERIFICATION_SOURCE_TTL = Duration.ofMinutes(10);
    private static final Duration LOGIN_FAILURE_TTL = Duration.ofMinutes(15);
    private static final int EMAIL_DAILY_LIMIT = 5;
    private static final int ACCOUNT_ACTION_SOURCE_LIMIT = 20;
    private static final int EMAIL_VERIFICATION_SOURCE_LIMIT = 10;
    private static final int LOGIN_EMAIL_SOURCE_LIMIT = 5;
    private static final int LOGIN_EMAIL_LIMIT = 10;
    private static final int LOGIN_SOURCE_LIMIT = 50;

    private static final DefaultRedisScript<Long> ACQUIRE_EMAIL_REQUEST = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
              return 0
            end
            local emailCount = tonumber(redis.call('GET', KEYS[2]) or '0')
            if emailCount >= tonumber(ARGV[2]) then
              return 0
            end
            redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
            emailCount = redis.call('INCR', KEYS[2])
            if emailCount == 1 then
              redis.call('EXPIRE', KEYS[2], ARGV[3])
            end
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> ACQUIRE_ACCOUNT_ACTION_REQUEST = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
              return 0
            end
            local emailCount = tonumber(redis.call('GET', KEYS[2]) or '0')
            if emailCount >= tonumber(ARGV[2]) then
              return 0
            end
            local sourceCount = tonumber(redis.call('GET', KEYS[3]) or '0')
            if sourceCount >= tonumber(ARGV[4]) then
              return 0
            end
            redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
            emailCount = redis.call('INCR', KEYS[2])
            if emailCount == 1 then
              redis.call('EXPIRE', KEYS[2], ARGV[3])
            end
            sourceCount = redis.call('INCR', KEYS[3])
            if sourceCount == 1 then
              redis.call('EXPIRE', KEYS[3], ARGV[5])
            end
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> IS_LOGIN_BLOCKED = new DefaultRedisScript<>("""
            for index, key in ipairs(KEYS) do
              if tonumber(redis.call('GET', key) or '0') >= tonumber(ARGV[index]) then
                return 1
              end
            end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> ACQUIRE_EMAIL_VERIFICATION_ATTEMPT = new DefaultRedisScript<>("""
            local attempts = tonumber(redis.call('GET', KEYS[1]) or '0')
            if attempts >= tonumber(ARGV[1]) then
              local ttl = tonumber(redis.call('TTL', KEYS[1]) or '-1')
              if ttl < 1 then
                ttl = tonumber(ARGV[2])
              end
              return -ttl
            end
            attempts = redis.call('INCR', KEYS[1])
            if attempts == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[2])
            end
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> RECORD_LOGIN_FAILURE = new DefaultRedisScript<>("""
            for index, key in ipairs(KEYS) do
              if tonumber(redis.call('GET', key) or '0') >= tonumber(ARGV[index]) then
                return 0
              end
            end
            for _, key in ipairs(KEYS) do
              local attempts = redis.call('INCR', key)
              if attempts == 1 then
                redis.call('EXPIRE', key, ARGV[3])
              end
            end
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> ACQUIRE_LOGIN_SOURCE_ATTEMPT = new DefaultRedisScript<>("""
            local attempts = tonumber(redis.call('GET', KEYS[1]) or '0')
            if attempts >= tonumber(ARGV[1]) then
              return 0
            end
            attempts = redis.call('INCR', KEYS[1])
            if attempts == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[2])
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final byte[] secret;

    public RedisMemberRateLimitStore(StringRedisTemplate redisTemplate, MemberRateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.secret = properties.getSecret().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean tryAcquireEmailRequest(String normalizedEmail) {
        Long acquired = redisTemplate.execute(
                ACQUIRE_EMAIL_REQUEST,
                List.of(emailCooldownKey(normalizedEmail), emailDailyKey(normalizedEmail)),
                seconds(EMAIL_COOLDOWN_TTL),
                String.valueOf(EMAIL_DAILY_LIMIT),
                seconds(EMAIL_DAILY_TTL)
        );
        return Long.valueOf(1).equals(acquired);
    }

    @Override
    public boolean tryAcquireAccountActionRequest(String normalizedEmail, String source) {
        Long acquired = redisTemplate.execute(
                ACQUIRE_ACCOUNT_ACTION_REQUEST,
                List.of(emailCooldownKey(normalizedEmail), emailDailyKey(normalizedEmail), accountActionSourceKey(source)),
                seconds(EMAIL_COOLDOWN_TTL),
                String.valueOf(EMAIL_DAILY_LIMIT),
                seconds(EMAIL_DAILY_TTL),
                String.valueOf(ACCOUNT_ACTION_SOURCE_LIMIT),
                seconds(ACCOUNT_ACTION_SOURCE_TTL)
        );
        return Long.valueOf(1).equals(acquired);
    }

    @Override
    public boolean isLoginBlocked(String normalizedEmail, String source) {
        Long blocked = redisTemplate.execute(
                IS_LOGIN_BLOCKED,
                List.of(loginEmailSourceKey(normalizedEmail, source), loginEmailKey(normalizedEmail), loginSourceKey(source)),
                String.valueOf(LOGIN_EMAIL_SOURCE_LIMIT),
                String.valueOf(LOGIN_EMAIL_LIMIT),
                String.valueOf(LOGIN_SOURCE_LIMIT)
        );
        return Long.valueOf(1).equals(blocked);
    }

    @Override
    public boolean tryAcquireLoginSourceAttempt(String source) {
        Long acquired = redisTemplate.execute(
                ACQUIRE_LOGIN_SOURCE_ATTEMPT,
                List.of(loginSourceKey(source)),
                String.valueOf(LOGIN_SOURCE_LIMIT),
                seconds(LOGIN_FAILURE_TTL)
        );
        return Long.valueOf(1).equals(acquired);
    }

    @Override
    public VerificationAttemptResult acquireEmailVerificationAttempt(String source) {
        Long result = redisTemplate.execute(
                ACQUIRE_EMAIL_VERIFICATION_ATTEMPT,
                List.of(emailVerificationAttemptKey(source)),
                String.valueOf(EMAIL_VERIFICATION_SOURCE_LIMIT),
                seconds(EMAIL_VERIFICATION_SOURCE_TTL)
        );
        if (result == null) {
            throw new IllegalStateException("Email verification rate-limit script returned no result");
        }
        if (result < 0) {
            return VerificationAttemptResult.reject(Math.max(1, -result));
        }
        return VerificationAttemptResult.permit();
    }

    @Override
    public boolean tryRecordLoginFailure(String normalizedEmail, String source) {
        Long recorded = redisTemplate.execute(
                RECORD_LOGIN_FAILURE,
                List.of(loginEmailSourceKey(normalizedEmail, source), loginEmailKey(normalizedEmail)),
                String.valueOf(LOGIN_EMAIL_SOURCE_LIMIT),
                String.valueOf(LOGIN_EMAIL_LIMIT),
                seconds(LOGIN_FAILURE_TTL)
        );
        return Long.valueOf(1).equals(recorded);
    }

    private String emailCooldownKey(String normalizedEmail) {
        return EMAIL_COOLDOWN_PREFIX + hash(normalizedEmail);
    }

    private String emailDailyKey(String normalizedEmail) {
        return EMAIL_DAILY_PREFIX + hash(normalizedEmail);
    }

    private String accountActionSourceKey(String source) {
        return ACCOUNT_ACTION_SOURCE_PREFIX + hash(source);
    }

    private String emailVerificationAttemptKey(String source) {
        return EMAIL_VERIFICATION_SOURCE_PREFIX + hash(source);
    }

    private String loginEmailSourceKey(String normalizedEmail, String source) {
        return LOGIN_EMAIL_SOURCE_PREFIX + hash(normalizedEmail + "\u0000" + source);
    }

    private String loginEmailKey(String normalizedEmail) {
        return LOGIN_EMAIL_PREFIX + hash(normalizedEmail);
    }

    private String loginSourceKey(String source) {
        return LOGIN_SOURCE_PREFIX + hash(source);
    }

    private String hash(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA-256 is unavailable", exception);
        }
    }

    private String seconds(Duration duration) {
        return String.valueOf(duration.toSeconds());
    }
}
