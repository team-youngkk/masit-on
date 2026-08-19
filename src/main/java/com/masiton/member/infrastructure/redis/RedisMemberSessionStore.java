package com.masiton.member.infrastructure.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.masiton.member.application.InvalidMemberSessionException;
import com.masiton.member.application.MemberSession;
import com.masiton.member.application.MemberSessionOwner;
import com.masiton.member.application.MemberSessionRevocation;
import com.masiton.member.application.port.out.MemberSessionRevocationRecoveryQueue;
import com.masiton.member.application.port.out.MemberSessionStore;
import com.masiton.common.security.MemberSessionSettings;

@Component
public class RedisMemberSessionStore implements MemberSessionStore {

    private static final String SESSION_PREFIX = "auth:session:data:";
    private static final String REFRESH_INDEX_PREFIX = "auth:session:refresh:";
    private static final String USED_REFRESH_INDEX_PREFIX = "auth:session:refresh:used:";
    private static final String MEMBER_SESSIONS_PREFIX = "auth:session:account:";
    private static final String MEMBER_SESSION_SEQUENCE_PREFIX = "auth:session:sequence:";
    private static final String MEMBER_SESSION_GENERATION_PREFIX = "auth:session:generation:";
    private static final String ISSUE_REVOKED_SENTINEL = "__REVOKED_DURING_ISSUE__";

    private static final DefaultRedisScript<Long> MIGRATE_LEGACY_SESSION_RECORD_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
              return 0
            end
            redis.call('SET', KEYS[1], ARGV[2], 'KEEPTTL')
            return 1
            """, Long.class);

    private static final DefaultRedisScript<String> ISSUE_SCRIPT = new DefaultRedisScript<>("""
            local function enqueueRevocation(sessionId, record)
              local expiresAt = tonumber(record.expiresAtEpochMillis)
              local now = tonumber(ARGV[9])
              if not expiresAt or expiresAt <= now then
                return
              end
              local recoveryKey = ARGV[10] .. sessionId
              local existing = redis.call('GET', recoveryKey)
              local revokedAt = now
              if existing then
                local separator = string.find(existing, ':')
                revokedAt = math.min(revokedAt, tonumber(string.sub(existing, 1, separator - 1)))
                expiresAt = math.max(expiresAt, tonumber(string.sub(existing, separator + 1)))
              end
              redis.call('SET', recoveryKey, revokedAt .. ':' .. expiresAt, 'PX', expiresAt - now)
              redis.call('ZADD', KEYS[6], now, sessionId)
            end
            local currentGeneration = redis.call('GET', KEYS[5]) or '0'
            if currentGeneration ~= ARGV[8] then
              return '__REVOKED_DURING_ISSUE__'
            end
            local existing = redis.call('ZRANGE', KEYS[3], 0, -1)
            for _, sessionId in ipairs(existing) do
              if not redis.call('GET', ARGV[4] .. sessionId) then
                redis.call('ZREM', KEYS[3], sessionId)
              end
            end
            local state = redis.call('GET', KEYS[4])
            local sequence = 0
            if state then
              local separator = string.find(state, ':')
              local stateCreatedAt = string.sub(state, 1, separator - 1)
              if stateCreatedAt == ARGV[1] then
                sequence = tonumber(string.sub(state, separator + 1)) + 1
              end
            end
            local score = tonumber(ARGV[1]) + sequence / 1000
            redis.call('ZADD', KEYS[3], score, ARGV[2])
            local evicted = ''
            while redis.call('ZCARD', KEYS[3]) > tonumber(ARGV[3]) do
              local oldest = redis.call('ZRANGE', KEYS[3], 0, 0)[1]
              local oldSessionKey = ARGV[4] .. oldest
              local oldRecord = redis.call('GET', oldSessionKey)
              if oldRecord then
                local old = cjson.decode(oldRecord)
                enqueueRevocation(oldest, old)
                redis.call('DEL', ARGV[5] .. old.tokenHash)
              end
              redis.call('DEL', oldSessionKey)
              redis.call('ZREM', KEYS[3], oldest)
              evicted = oldest
            end
            redis.call('SET', KEYS[1], ARGV[6], 'EX', ARGV[7])
            redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[7])
            redis.call('EXPIRE', KEYS[3], ARGV[7])
            redis.call('SET', KEYS[4], ARGV[1] .. ':' .. sequence, 'EX', ARGV[7])
            return evicted
            """, String.class);

    private static final DefaultRedisScript<String> REVOKE_ALL_SCRIPT = new DefaultRedisScript<>("""
            local function nowEpochMillis()
              local time = redis.call('TIME')
              return tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            end
            local function enqueueRevocation(sessionId, record)
              local expiresAt = tonumber(record.expiresAtEpochMillis)
              local now = nowEpochMillis()
              if not expiresAt or expiresAt <= now then
                return
              end
              local recoveryKey = ARGV[3] .. sessionId
              local existing = redis.call('GET', recoveryKey)
              local revokedAt = now
              if existing then
                local separator = string.find(existing, ':')
                revokedAt = math.min(revokedAt, tonumber(string.sub(existing, 1, separator - 1)))
                expiresAt = math.max(expiresAt, tonumber(string.sub(existing, separator + 1)))
              end
              redis.call('SET', recoveryKey, revokedAt .. ':' .. expiresAt, 'PX', expiresAt - now)
              redis.call('ZADD', KEYS[4], now, sessionId)
            end
            local sessionIds = redis.call('ZRANGE', KEYS[1], 0, -1)
            for _, sessionId in ipairs(sessionIds) do
              local sessionKey = ARGV[1] .. sessionId
              local serialized = redis.call('GET', sessionKey)
              if serialized then
                local record = cjson.decode(serialized)
                enqueueRevocation(sessionId, record)
                redis.call('DEL', ARGV[2] .. record.tokenHash)
              end
              redis.call('DEL', sessionKey)
            end
            redis.call('DEL', KEYS[1])
            redis.call('DEL', KEYS[2])
            redis.call('INCR', KEYS[3])
            if #sessionIds == 0 then
              return '[]'
            end
            return cjson.encode(sessionIds)
            """, String.class);

    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            local function enqueueRevocation(sessionId, record)
              local expiresAt = tonumber(record.expiresAtEpochMillis)
              local now = tonumber(ARGV[7])
              if not expiresAt or expiresAt <= now then
                return
              end
              local recoveryKey = ARGV[8] .. sessionId
              local existing = redis.call('GET', recoveryKey)
              local revokedAt = now
              if existing then
                local separator = string.find(existing, ':')
                revokedAt = math.min(revokedAt, tonumber(string.sub(existing, 1, separator - 1)))
                expiresAt = math.max(expiresAt, tonumber(string.sub(existing, separator + 1)))
              end
              redis.call('SET', recoveryKey, revokedAt .. ':' .. expiresAt, 'PX', expiresAt - now)
              redis.call('ZADD', KEYS[4], now, sessionId)
            end
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
                enqueueRevocation(sessionId, record)
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
              enqueueRevocation(sessionId, record)
              redis.call('DEL', KEYS[1])
              redis.call('DEL', sessionKey)
              redis.call('ZREM', ARGV[6] .. record.memberId, sessionId)
              return -1
            end
            redis.call('DEL', KEYS[1])
            redis.call('SET', KEYS[2], sessionId, 'EX', ARGV[5])
            redis.call('SET', KEYS[3], sessionId, 'EX', ARGV[5])
            redis.call('SET', sessionKey, ARGV[4], 'EX', ARGV[5])
            redis.call('EXPIRE', ARGV[6] .. record.memberId, ARGV[5])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MemberRefreshTokenFactory refreshTokenFactory;
    private final MemberSessionRevocationRecoveryQueue recoveryQueue;
    private final int maxSessions;
    private final Clock clock;

    public RedisMemberSessionStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            MemberRefreshTokenFactory refreshTokenFactory,
            MemberSessionRevocationRecoveryQueue recoveryQueue,
            MemberSessionSettings settings,
            Clock memberSessionClock
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.refreshTokenFactory = refreshTokenFactory;
        this.recoveryQueue = recoveryQueue;
        this.maxSessions = settings.maxSessions();
        this.clock = memberSessionClock;
    }

    @Override
    public MemberSession issue(String memberId, Duration ttl) {
        return issue(memberId, ttl, maxSessions);
    }

    @Override
    public MemberSession issue(String memberId, Duration ttl, int requestedMaxSessions) {
        String expectedGeneration = memberSessionGeneration(memberId);
        migrateLegacySessionRecords(memberId);
        String sessionId = UUID.randomUUID().toString();
        String refreshToken = refreshTokenFactory.create();
        Instant createdAt = Instant.now(clock);
        Instant expiresAt = createdAt.plus(ttl);
        MemberSessionRecord record = new MemberSessionRecord(memberId, hash(refreshToken), createdAt, expiresAt);
        String evictedSessionId = redisTemplate.execute(
                ISSUE_SCRIPT,
                List.of(
                        sessionKey(sessionId),
                        refreshIndexKey(refreshToken),
                        memberSessionsKey(memberId),
                        memberSessionSequenceKey(memberId),
                        memberSessionGenerationKey(memberId),
                        RedisMemberSessionRevocationRecoveryQueue.DUE_KEY
                ),
                String.valueOf(createdAt.toEpochMilli()),
                sessionId,
                String.valueOf(requestedMaxSessions),
                SESSION_PREFIX,
                REFRESH_INDEX_PREFIX,
                serialize(record),
                String.valueOf(ttl.toSeconds()),
                expectedGeneration,
                String.valueOf(createdAt.toEpochMilli()),
                RedisMemberSessionRevocationRecoveryQueue.RECOVERY_PREFIX
        );
        if (ISSUE_REVOKED_SENTINEL.equals(evictedSessionId)) {
            throw new InvalidMemberSessionException();
        }
        return new MemberSession(memberId, sessionId, refreshToken, revokedSessionIds(evictedSessionId));
    }

    @Override
    public MemberSession rotate(String refreshToken, Duration ttl) {
        String nextRefreshToken = refreshTokenFactory.create();
        Instant expiresAt = Instant.now(clock).plus(ttl);
        String oldHash = hash(refreshToken);

        String sessionId = redisTemplate.opsForValue().get(refreshIndexKey(refreshToken));
        if (sessionId == null) {
            sessionId = redisTemplate.opsForValue().get(usedRefreshIndexKey(refreshToken));
        }
        if (sessionId == null) {
            throw new InvalidMemberSessionException();
        }
        migrateLegacySessionRecord(sessionId);
        MemberSessionRecord current = read(redisTemplate.opsForValue().get(sessionKey(sessionId)));
        MemberSessionRecord rotated = new MemberSessionRecord(
                current.memberId(),
                hash(nextRefreshToken),
                current.createdAt(),
                expiresAt
        );
        Long result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(
                        refreshIndexKey(refreshToken),
                        usedRefreshIndexKey(refreshToken),
                        refreshIndexKey(nextRefreshToken),
                        RedisMemberSessionRevocationRecoveryQueue.DUE_KEY
                ),
                SESSION_PREFIX,
                REFRESH_INDEX_PREFIX,
                oldHash,
                serialize(rotated),
                String.valueOf(ttl.toSeconds()),
                MEMBER_SESSIONS_PREFIX,
                String.valueOf(Instant.now(clock).toEpochMilli()),
                RedisMemberSessionRevocationRecoveryQueue.RECOVERY_PREFIX
        );
        if (result == null || result != 1L) {
            throw new InvalidMemberSessionException(revokedSessionIds(sessionId));
        }
        return new MemberSession(current.memberId(), sessionId, nextRefreshToken, java.util.Set.of());
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
    public Optional<MemberSessionOwner> findSession(String refreshToken) {
        String sessionId = redisTemplate.opsForValue().get(refreshIndexKey(refreshToken));
        if (sessionId == null) {
            sessionId = redisTemplate.opsForValue().get(usedRefreshIndexKey(refreshToken));
        }
        if (sessionId == null) {
            return Optional.empty();
        }
        try {
            MemberSessionRecord record = read(redisTemplate.opsForValue().get(sessionKey(sessionId)));
            return Optional.of(new MemberSessionOwner(record.memberId(), sessionId));
        } catch (InvalidMemberSessionException exception) {
            redisTemplate.delete(List.of(refreshIndexKey(refreshToken), usedRefreshIndexKey(refreshToken)));
            return Optional.empty();
        }
    }

    @Override
    public java.util.Set<String> activeSessionIds(String memberId) {
        java.util.Set<String> sessionIds = redisTemplate.opsForZSet().range(memberSessionsKey(memberId), 0, -1);
        return sessionIds == null ? java.util.Set.of() : java.util.Set.copyOf(sessionIds);
    }

    @Override
    public void revoke(String memberId, String sessionId) {
        MemberSessionRecord record = read(redisTemplate.opsForValue().get(sessionKey(sessionId)));
        if (!memberId.equals(record.memberId())) {
            throw new InvalidMemberSessionException();
        }
        Instant now = Instant.now(clock);
        recoveryQueue.enqueue(new MemberSessionRevocation(UUID.fromString(sessionId), now, record.expiresAt()), now);
        redisTemplate.delete(List.of(sessionKey(sessionId), REFRESH_INDEX_PREFIX + record.tokenHash()));
        redisTemplate.opsForZSet().remove(memberSessionsKey(memberId), sessionId);
    }

    @Override
    public java.util.Set<String> revokeAll(String memberId) {
        migrateLegacySessionRecords(memberId);
        String serializedSessionIds = redisTemplate.execute(
                REVOKE_ALL_SCRIPT,
                List.of(
                        memberSessionsKey(memberId),
                        memberSessionSequenceKey(memberId),
                        memberSessionGenerationKey(memberId),
                        RedisMemberSessionRevocationRecoveryQueue.DUE_KEY
                ),
                SESSION_PREFIX,
                REFRESH_INDEX_PREFIX,
                RedisMemberSessionRevocationRecoveryQueue.RECOVERY_PREFIX
        );
        try {
            String[] sessionIds = objectMapper.readValue(
                    serializedSessionIds == null ? "[]" : serializedSessionIds, String[].class);
            return java.util.Set.of(sessionIds);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not read revoked member sessions", exception);
        }
    }

    private MemberSessionRecord read(String serialized) {
        if (serialized == null) {
            throw new InvalidMemberSessionException();
        }
        try {
            JsonNode record = objectMapper.readTree(serialized);
            if (record instanceof ObjectNode objectRecord
                    && !objectRecord.has("expiresAtEpochMillis")
                    && objectRecord.hasNonNull("expiresAt")) {
                objectRecord.put(
                        "expiresAtEpochMillis",
                        Instant.parse(objectRecord.get("expiresAt").asText()).toEpochMilli()
                );
            }
            return objectMapper.treeToValue(record, MemberSessionRecord.class);
        } catch (JacksonException | DateTimeException exception) {
            throw new IllegalStateException("Could not read member session state", exception);
        }
    }

    private void migrateLegacySessionRecords(String memberId) {
        java.util.Set<String> sessionIds = redisTemplate.opsForZSet().range(memberSessionsKey(memberId), 0, -1);
        if (sessionIds == null) {
            return;
        }
        sessionIds.forEach(this::migrateLegacySessionRecord);
    }

    private void migrateLegacySessionRecord(String sessionId) {
        String key = sessionKey(sessionId);
        String serialized = redisTemplate.opsForValue().get(key);
        if (serialized == null) {
            return;
        }
        if (hasExpiryEpochMillis(serialized)) {
            return;
        }
        MemberSessionRecord record = read(serialized);
        MemberSessionRecord migrated = new MemberSessionRecord(
                record.memberId(), record.tokenHash(), record.createdAt(), record.expiresAt());
        redisTemplate.execute(
                MIGRATE_LEGACY_SESSION_RECORD_SCRIPT,
                List.of(key),
                serialized,
                serialize(migrated)
        );
    }

    private boolean hasExpiryEpochMillis(String serialized) {
        try {
            return objectMapper.readTree(serialized).hasNonNull("expiresAtEpochMillis");
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

    private String memberSessionSequenceKey(String memberId) {
        return MEMBER_SESSION_SEQUENCE_PREFIX + memberId;
    }

    private String memberSessionGenerationKey(String memberId) {
        return MEMBER_SESSION_GENERATION_PREFIX + memberId;
    }

    private String memberSessionGeneration(String memberId) {
        String generation = redisTemplate.opsForValue().get(memberSessionGenerationKey(memberId));
        return generation == null ? "0" : generation;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private java.util.Set<String> revokedSessionIds(String sessionId) {
        return sessionId == null || sessionId.isBlank() ? java.util.Set.of() : java.util.Set.of(sessionId);
    }

    private record MemberSessionRecord(String memberId, String tokenHash, Instant createdAt, Instant expiresAt,
            long expiresAtEpochMillis) {

        private MemberSessionRecord(String memberId, String tokenHash, Instant createdAt, Instant expiresAt) {
            this(memberId, tokenHash, createdAt, expiresAt, expiresAt.toEpochMilli());
        }
    }
}
