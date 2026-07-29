package com.masiton.member.infrastructure.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.masiton.member.application.InvalidMemberSessionException;
import com.masiton.member.application.MemberSession;
import com.masiton.member.application.port.out.MemberSessionStore;
import com.masiton.security.infrastructure.configuration.SecurityProperties;
import com.masiton.security.infrastructure.redis.RefreshTokenFactory;

@Component
public class RedisMemberSessionStore implements MemberSessionStore {

    private static final String SESSION_PREFIX = "auth:member:session:";
    private static final String REFRESH_INDEX_PREFIX = "auth:member:refresh:";
    private static final String USED_REFRESH_INDEX_PREFIX = "auth:member:refresh:used:";
    private static final String MEMBER_SESSIONS_PREFIX = "auth:member:sessions:";

    private static final DefaultRedisScript<Long> ISSUE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[3], '-inf', ARGV[1])
            local now = redis.call('TIME')
            local score = tonumber(now[1]) * 1000000 + tonumber(now[2])
            redis.call('ZADD', KEYS[3], score, ARGV[2])
            while redis.call('ZCARD', KEYS[3]) > tonumber(ARGV[3]) do
              local oldest = redis.call('ZRANGE', KEYS[3], 0, 0)[1]
              local oldSessionKey = ARGV[4] .. oldest
              local oldRecord = redis.call('GET', oldSessionKey)
              if oldRecord then
                local old = cjson.decode(oldRecord)
                redis.call('DEL', ARGV[5] .. old.tokenHash)
              end
              redis.call('DEL', oldSessionKey)
              redis.call('ZREM', KEYS[3], oldest)
            end
            redis.call('SET', KEYS[1], ARGV[6], 'EX', ARGV[7])
            redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[7])
            redis.call('EXPIRE', KEYS[3], ARGV[7])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            local sessionId = redis.call('GET', KEYS[1])
            if not sessionId then
              sessionId = redis.call('GET', KEYS[2])
              if not sessionId then
                return 0
              end
              local replayedSessionKey = ARGV[1] .. sessionId
              local replayed = redis.call('GET', replayedSessionKey)
              if replayed then
                local record = cjson.decode(replayed)
                redis.call('DEL', ARGV[2] .. record.tokenHash)
                redis.call('DEL', replayedSessionKey)
                redis.call('ZREM', ARGV[6] .. record.memberId, sessionId)
              end
              return -1
            end
            local sessionKey = ARGV[1] .. sessionId
            local stored = redis.call('GET', sessionKey)
            if not stored then
              redis.call('DEL', KEYS[1])
              return 0
            end
            local record = cjson.decode(stored)
            if record.tokenHash ~= ARGV[3] then
              redis.call('DEL', KEYS[1])
              redis.call('DEL', sessionKey)
              redis.call('ZREM', ARGV[6] .. record.memberId, sessionId)
              return -1
            end
            redis.call('DEL', KEYS[1])
            redis.call('SET', KEYS[2], sessionId, 'EX', ARGV[5])
            redis.call('SET', KEYS[3], sessionId, 'EX', ARGV[5])
            redis.call('SET', sessionKey, ARGV[4], 'EX', ARGV[5])
            redis.call('ZADD', ARGV[6] .. record.memberId, ARGV[7], sessionId)
            redis.call('EXPIRE', ARGV[6] .. record.memberId, ARGV[5])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RefreshTokenFactory refreshTokenFactory;
    private final int maxSessions;

    public RedisMemberSessionStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RefreshTokenFactory refreshTokenFactory,
            SecurityProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.refreshTokenFactory = refreshTokenFactory;
        this.maxSessions = properties.getMember().getMaxSessions();
    }

    @Override
    public MemberSession issue(String memberId, Duration ttl) {
        String sessionId = UUID.randomUUID().toString();
        String refreshToken = refreshTokenFactory.create();
        Instant expiresAt = Instant.now().plus(ttl);
        MemberSessionRecord record = new MemberSessionRecord(memberId, hash(refreshToken), expiresAt);
        Long result = redisTemplate.execute(
                ISSUE_SCRIPT,
                List.of(sessionKey(sessionId), refreshIndexKey(refreshToken), memberSessionsKey(memberId)),
                String.valueOf(Instant.now().toEpochMilli()),
                sessionId,
                String.valueOf(maxSessions),
                SESSION_PREFIX,
                REFRESH_INDEX_PREFIX,
                serialize(record),
                String.valueOf(ttl.toSeconds())
        );
        if (result == null || result != 1L) {
            throw new IllegalStateException("Could not issue member session");
        }
        return new MemberSession(memberId, sessionId, refreshToken);
    }

    @Override
    public MemberSession rotate(String refreshToken, Duration ttl) {
        String nextRefreshToken = refreshTokenFactory.create();
        Instant expiresAt = Instant.now().plus(ttl);
        String oldHash = hash(refreshToken);
        MemberSessionRecord nextRecord = new MemberSessionRecord(null, hash(nextRefreshToken), expiresAt);

        String sessionId = redisTemplate.opsForValue().get(refreshIndexKey(refreshToken));
        if (sessionId == null) {
            sessionId = redisTemplate.opsForValue().get(usedRefreshIndexKey(refreshToken));
        }
        if (sessionId == null) {
            throw new InvalidMemberSessionException();
        }
        MemberSessionRecord current = read(redisTemplate.opsForValue().get(sessionKey(sessionId)));
        MemberSessionRecord rotated = new MemberSessionRecord(current.memberId(), nextRecord.tokenHash(), expiresAt);
        Long result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(
                        refreshIndexKey(refreshToken),
                        usedRefreshIndexKey(refreshToken),
                        refreshIndexKey(nextRefreshToken)
                ),
                SESSION_PREFIX,
                REFRESH_INDEX_PREFIX,
                oldHash,
                serialize(rotated),
                String.valueOf(ttl.toSeconds()),
                MEMBER_SESSIONS_PREFIX,
                String.valueOf(expiresAt.toEpochMilli())
        );
        if (result == null || result != 1L) {
            throw new InvalidMemberSessionException();
        }
        return new MemberSession(current.memberId(), sessionId, nextRefreshToken);
    }

    @Override
    public boolean matches(String memberId, String refreshToken) {
        String sessionId = redisTemplate.opsForValue().get(refreshIndexKey(refreshToken));
        if (sessionId == null) {
            return false;
        }
        try {
            MemberSessionRecord record = read(redisTemplate.opsForValue().get(sessionKey(sessionId)));
            return memberId.equals(record.memberId()) && MessageDigest.isEqual(
                    record.tokenHash().getBytes(StandardCharsets.US_ASCII),
                    hash(refreshToken).getBytes(StandardCharsets.US_ASCII)
            );
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public void revoke(String memberId, String sessionId) {
        MemberSessionRecord record = read(redisTemplate.opsForValue().get(sessionKey(sessionId)));
        if (!memberId.equals(record.memberId())) {
            throw new InvalidMemberSessionException();
        }
        redisTemplate.delete(List.of(sessionKey(sessionId), REFRESH_INDEX_PREFIX + record.tokenHash()));
        redisTemplate.opsForZSet().remove(memberSessionsKey(memberId), sessionId);
    }

    @Override
    public void revokeAll(String memberId) {
        String sessionsKey = memberSessionsKey(memberId);
        java.util.Set<String> sessionIds = redisTemplate.opsForZSet().range(sessionsKey, 0, -1);
        if (sessionIds != null) {
            for (String sessionId : sessionIds) {
                String serialized = redisTemplate.opsForValue().get(sessionKey(sessionId));
                if (serialized != null) {
                    MemberSessionRecord record = read(serialized);
                    redisTemplate.delete(List.of(sessionKey(sessionId), REFRESH_INDEX_PREFIX + record.tokenHash()));
                }
            }
        }
        redisTemplate.delete(sessionsKey);
    }

    private MemberSessionRecord read(String serialized) {
        if (serialized == null) {
            throw new InvalidMemberSessionException();
        }
        try {
            return objectMapper.readValue(serialized, MemberSessionRecord.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not read member session state", exception);
        }
    }

    private String serialize(MemberSessionRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize member session state", exception);
        }
    }

    private String sessionKey(String sessionId) {
        return SESSION_PREFIX + sessionId;
    }

    private String refreshIndexKey(String refreshToken) {
        return REFRESH_INDEX_PREFIX + hash(refreshToken);
    }

    private String usedRefreshIndexKey(String refreshToken) {
        return USED_REFRESH_INDEX_PREFIX + hash(refreshToken);
    }

    private String memberSessionsKey(String memberId) {
        return MEMBER_SESSIONS_PREFIX + memberId;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record MemberSessionRecord(String memberId, String tokenHash, Instant expiresAt) {
    }
}
