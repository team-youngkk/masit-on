package com.masiton.common.idempotency.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.masiton.common.idempotency.application.IdempotencyActorType;
import com.masiton.common.idempotency.application.IdempotencyApiScope;
import com.masiton.common.idempotency.application.IdempotencyRecord;
import com.masiton.common.idempotency.application.IdempotencyRecordAlreadyExistsException;
import com.masiton.common.idempotency.application.IdempotencyRequest;
import com.masiton.common.idempotency.application.IdempotencyResponse;
import com.masiton.common.idempotency.application.port.out.IdempotencyRecordStore;

@Repository
public class JdbcIdempotencyRecordStore implements IdempotencyRecordStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcIdempotencyRecordStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<IdempotencyRecord> find(IdempotencyRequest request) {
        List<IdempotencyRecord> records = jdbcTemplate.query("""
                SELECT id, actor_type, actor_id, api_scope, key_hash, request_hash,
                       response_status, response_body::text AS response_body, resource_id,
                       created_at, expires_at
                  FROM idempotency_record
                 WHERE actor_type = ? AND actor_id = ? AND api_scope = ? AND key_hash = ?
                """, this::mapRecord,
                request.actorType().name(), request.actorId(), request.apiScope().value(), request.keyHash());
        return records.stream().findFirst();
    }

    @Override
    public int deleteIfExpired(IdempotencyRequest request, OffsetDateTime cutoff) {
        return jdbcTemplate.update("""
                DELETE FROM idempotency_record
                 WHERE actor_type = ? AND actor_id = ? AND api_scope = ? AND key_hash = ?
                   AND expires_at <= ?
                """, request.actorType().name(), request.actorId(), request.apiScope().value(),
                request.keyHash(), cutoff);
    }

    @Override
    public void save(IdempotencyRecord record) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO idempotency_record (
                        id, actor_type, actor_id, api_scope, key_hash, request_hash,
                        response_status, response_body, resource_id, created_at, expires_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)
                    """, record.id(), record.actorType().name(), record.actorId(),
                    record.apiScope().value(), record.keyHash(), record.requestHash(),
                    record.response().status(), record.response().body(), record.response().resourceId(),
                    record.createdAt(), record.expiresAt());
        } catch (DuplicateKeyException exception) {
            throw new IdempotencyRecordAlreadyExistsException(exception);
        }
    }

    @Override
    public int deleteExpiredBatch(OffsetDateTime cutoff, int batchSize) {
        return jdbcTemplate.update("""
                DELETE FROM idempotency_record target
                 WHERE target.id IN (
                     SELECT id
                       FROM idempotency_record
                      WHERE expires_at <= ?
                      ORDER BY expires_at, id
                      LIMIT ?
                      FOR UPDATE SKIP LOCKED
                 )
                """, cutoff, batchSize);
    }

    private IdempotencyRecord mapRecord(ResultSet resultSet, int rowNumber) throws SQLException {
        return new IdempotencyRecord(
                resultSet.getObject("id", java.util.UUID.class),
                IdempotencyActorType.valueOf(resultSet.getString("actor_type")),
                resultSet.getObject("actor_id", java.util.UUID.class),
                IdempotencyApiScope.fromValue(resultSet.getString("api_scope")),
                resultSet.getBytes("key_hash"),
                resultSet.getBytes("request_hash"),
                new IdempotencyResponse(
                        resultSet.getInt("response_status"),
                        resultSet.getString("response_body"),
                        resultSet.getObject("resource_id", java.util.UUID.class)),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("expires_at", OffsetDateTime.class));
    }
}
