package com.masiton.member.infrastructure.redis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.masiton.member.application.MemberSessionRevocation;
import com.masiton.member.application.port.out.MemberSessionRevocationRecoveryQueue;

@Component
public class RedisMemberSessionRevocationRecoveryQueue implements MemberSessionRevocationRecoveryQueue {

    static final String RECOVERY_PREFIX = "auth:member:session:revocation:recovery:";
    static final String DUE_KEY = RECOVERY_PREFIX + "due";
    private static final long RETRY_DELAY_MILLIS = 15 * 60 * 1000L;

    private static final DefaultRedisScript<Long> ENQUEUE_SCRIPT = new DefaultRedisScript<>("""
            local existing = redis.call('GET', KEYS[1])
            local revokedAt = tonumber(ARGV[1])
            local expiresAt = tonumber(ARGV[2])
            if existing then
              local separator = string.find(existing, ':')
              local existingRevokedAt = tonumber(string.sub(existing, 1, separator - 1))
              local existingExpiresAt = tonumber(string.sub(existing, separator + 1))
              revokedAt = math.min(revokedAt, existingRevokedAt)
              expiresAt = math.max(expiresAt, existingExpiresAt)
            end
            local ttl = expiresAt - tonumber(ARGV[3])
            if ttl <= 0 then
              return 0
            end
            redis.call('SET', KEYS[1], revokedAt .. ':' .. expiresAt, 'PX', ttl)
            redis.call('ZADD', KEYS[2], ARGV[3], ARGV[4])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<List> CLAIM_DUE_SCRIPT = new DefaultRedisScript<>("""
            local sessionIds = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, ARGV[2])
            local claimed = {}
            for _, sessionId in ipairs(sessionIds) do
              local value = redis.call('GET', ARGV[4] .. sessionId)
              if value then
                redis.call('ZADD', KEYS[1], ARGV[3], sessionId)
                table.insert(claimed, sessionId)
                table.insert(claimed, value)
              else
                redis.call('ZREM', KEYS[1], sessionId)
              end
            end
            return claimed
            """, List.class);

    private static final DefaultRedisScript<Long> COMPLETE_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if not value then
              redis.call('ZREM', KEYS[2], ARGV[1])
              return 1
            end
            local separator = string.find(value, ':')
            local revokedAt = tonumber(string.sub(value, 1, separator - 1))
            local expiresAt = tonumber(string.sub(value, separator + 1))
            if tonumber(ARGV[2]) <= revokedAt and tonumber(ARGV[3]) >= expiresAt then
              redis.call('DEL', KEYS[1])
              redis.call('ZREM', KEYS[2], ARGV[1])
              return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisMemberSessionRevocationRecoveryQueue(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void enqueue(MemberSessionRevocation revocation, Instant now) {
        redisTemplate.execute(
                ENQUEUE_SCRIPT,
                List.of(recoveryKey(revocation.sessionId()), DUE_KEY),
                String.valueOf(revocation.revokedAt().toEpochMilli()),
                String.valueOf(revocation.expiresAt().toEpochMilli()),
                String.valueOf(now.toEpochMilli()),
                revocation.sessionId().toString()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<MemberSessionRevocation> claimDue(Instant now, int limit) {
        List<String> claimed = redisTemplate.execute(
                CLAIM_DUE_SCRIPT,
                List.of(DUE_KEY),
                String.valueOf(now.toEpochMilli()),
                String.valueOf(limit),
                String.valueOf(now.toEpochMilli() + RETRY_DELAY_MILLIS),
                RECOVERY_PREFIX
        );
        if (claimed == null || claimed.isEmpty()) {
            return List.of();
        }

        List<MemberSessionRevocation> revocations = new ArrayList<>(claimed.size() / 2);
        for (int index = 0; index < claimed.size(); index += 2) {
            String[] values = claimed.get(index + 1).split(":", 2);
            revocations.add(new MemberSessionRevocation(
                    UUID.fromString(claimed.get(index)),
                    Instant.ofEpochMilli(Long.parseLong(values[0])),
                    Instant.ofEpochMilli(Long.parseLong(values[1]))
            ));
        }
        return List.copyOf(revocations);
    }

    @Override
    public void complete(MemberSessionRevocation revocation) {
        redisTemplate.execute(
                COMPLETE_SCRIPT,
                List.of(recoveryKey(revocation.sessionId()), DUE_KEY),
                revocation.sessionId().toString(),
                String.valueOf(revocation.revokedAt().toEpochMilli()),
                String.valueOf(revocation.expiresAt().toEpochMilli())
        );
    }

    private String recoveryKey(UUID sessionId) {
        return RECOVERY_PREFIX + sessionId;
    }
}
