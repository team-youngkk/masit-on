package com.masiton.ai.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.masiton.ai.application.port.out.AiExtractionJobStore;
import com.masiton.ai.application.port.out.dto.AiExtractionJobView;

@Repository
public class JdbcAiExtractionJobStore implements AiExtractionJobStore {

    private static final String BASE_SELECT = """
            SELECT job.id, job.source, job.youtube_channel_id, job.youtube_video_id, job.video_url,
                   job.execution_status, job.result_completeness, snapshot.review_status,
                   job.provider, job.model_version, job.prompt_version, job.schema_version,
                   job.attempt_count, job.created_at, job.started_at, job.finished_at
              FROM ai_extraction_job job
              LEFT JOIN LATERAL (
                    SELECT review_status
                      FROM ai_candidate_snapshot
                     WHERE job_id = job.id
                     ORDER BY snapshot_version DESC, created_at DESC, id DESC
                     LIMIT 1
              ) snapshot ON true
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAiExtractionJobStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<AiExtractionJobView> findByVideoIdAndInputMode(String videoId, String inputMode,
                                                                    String provider, String modelVersion,
                                                                    String promptVersion, String schemaVersion) {
        List<AiExtractionJobView> rows = jdbcTemplate.query(BASE_SELECT + """
                 WHERE job.youtube_video_id = ? AND job.input_mode = ?
                   AND job.provider = ? AND job.model_version = ? AND job.prompt_version = ? AND job.schema_version = ?
                 ORDER BY job.created_at DESC, job.id DESC
                 LIMIT 1
                """, this::map, videoId, inputMode, provider, modelVersion, promptVersion, schemaVersion);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<AiExtractionJobView> findByVideoIdAndInputHash(String videoId, byte[] inputHash,
                                                                    String provider, String modelVersion,
                                                                    String promptVersion, String schemaVersion) {
        List<AiExtractionJobView> rows = jdbcTemplate.query(BASE_SELECT + """
                 WHERE job.youtube_video_id = ? AND job.input_hash = ?
                   AND job.provider = ? AND job.model_version = ? AND job.prompt_version = ? AND job.schema_version = ?
                 ORDER BY job.created_at DESC, job.id DESC
                 LIMIT 1
                """, this::map, videoId, inputHash, provider, modelVersion, promptVersion, schemaVersion);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<AiExtractionJobView> find(String channelId, String videoId, byte[] inputHash,
                                               String provider, String modelVersion, String promptVersion,
                                               String schemaVersion) {
        List<AiExtractionJobView> rows = jdbcTemplate.query(BASE_SELECT + """
                 WHERE job.youtube_channel_id = ? AND job.youtube_video_id = ? AND job.input_hash = ?
                   AND job.provider = ? AND job.model_version = ? AND job.prompt_version = ? AND job.schema_version = ?
                 LIMIT 1
                """, this::map, channelId, videoId, inputHash, provider, modelVersion, promptVersion, schemaVersion);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<AiExtractionJobView> insert(AiExtractionJobDraft draft) {
        int inserted = jdbcTemplate.update("""
                INSERT INTO ai_extraction_job (
                    id, source, priority, youtube_channel_id, youtube_video_id, video_url,
                    input_mode, input_hash, provider, model_version, prompt_version, schema_version, retry_reason, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (youtube_channel_id, youtube_video_id, input_hash, provider,
                             model_version, prompt_version, schema_version) DO NOTHING
                """, draft.jobId(), draft.source(), draft.priority(), draft.channelId(), draft.videoId(),
                draft.videoUrl().toString(), draft.inputMode(), draft.inputHash(), draft.provider(),
                draft.modelVersion(), draft.promptVersion(), draft.schemaVersion(), draft.retryReason(), draft.createdAt());
        if (inserted == 0) return Optional.empty();
        return Optional.of(new AiExtractionJobView(draft.jobId(), draft.source(), draft.channelId(), draft.videoId(),
                draft.videoUrl().toString(), "QUEUED", null, null, draft.provider(), draft.modelVersion(),
                draft.promptVersion(), draft.schemaVersion(), 0, draft.createdAt(), null, null, false));
    }

    @Override
    public void storeTemporaryInput(UUID jobId, byte[] ciphertext, String encryptionKeyId, OffsetDateTime expiresAt) {
        jdbcTemplate.update("""
                INSERT INTO ai_extraction_temporary_input (job_id, ciphertext, encryption_key_id, expires_at)
                VALUES (?, ?, ?, ?)
                """, jobId, ciphertext, encryptionKeyId, expiresAt);
    }

    @Override
    public int deleteTemporaryInput(UUID jobId) {
        return jdbcTemplate.update("DELETE FROM ai_extraction_temporary_input WHERE job_id = ?", jobId);
    }

    @Override
    public int deleteExpiredTemporaryInputs(OffsetDateTime cutoff) {
        return jdbcTemplate.update("""
                DELETE FROM ai_extraction_temporary_input input
                 USING ai_extraction_job job
                 WHERE input.job_id = job.id
                   AND input.expires_at <= ?
                   AND job.execution_status IN ('SUCCEEDED', 'FAILED')
                """, cutoff);
    }

    private AiExtractionJobView map(ResultSet rs, int rowNum) throws SQLException {
        return new AiExtractionJobView(rs.getObject("id", UUID.class), rs.getString("source"),
                rs.getString("youtube_channel_id"), rs.getString("youtube_video_id"), rs.getString("video_url"),
                rs.getString("execution_status"), rs.getString("result_completeness"), rs.getString("review_status"),
                rs.getString("provider"), rs.getString("model_version"), rs.getString("prompt_version"),
                rs.getString("schema_version"), rs.getInt("attempt_count"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("finished_at", OffsetDateTime.class), false);
    }
}
