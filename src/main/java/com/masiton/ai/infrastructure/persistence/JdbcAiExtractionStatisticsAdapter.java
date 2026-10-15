package com.masiton.ai.infrastructure.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.masiton.ai.application.port.out.AiExtractionStatisticsPort;

@Repository
public class JdbcAiExtractionStatisticsAdapter implements AiExtractionStatisticsPort {
    private final JdbcTemplate jdbc;

    public JdbcAiExtractionStatisticsAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Statistics query(Filter filter) {
        StringBuilder where = new StringBuilder(" WHERE created_at >= ? AND created_at < ?");
        List<Object> args = new ArrayList<>(List.of(filter.from(), filter.to()));
        addFilter(where, args, "source", filter.source());
        addFilter(where, args, "model_version", filter.modelVersion());
        addFilter(where, args, "prompt_version", filter.promptVersion());
        addFilter(where, args, "schema_version", filter.schemaVersion());
        String sql = "WITH jobs AS (SELECT * FROM ai_extraction_job" + where + """
                ), measures AS (
                    SELECT source, model_version, prompt_version, schema_version,
                           'JOB' AS kind, execution_status AS status, NULL::varchar AS reason,
                           false AS registered, count(*) AS n
                    FROM jobs GROUP BY source, model_version, prompt_version, schema_version, execution_status
                    UNION ALL
                    SELECT j.source, j.model_version, j.prompt_version, j.schema_version,
                           'UNIT', u.review_status, u.block_reason,
                           (u.registered_restaurant_id IS NOT NULL AND u.registered_creator_id IS NOT NULL
                            AND u.registered_video_id IS NOT NULL AND u.registered_visit_id IS NOT NULL
                            AND u.rolled_back_at IS NULL AND u.discarded_at IS NULL), count(*)
                    FROM jobs j
                    JOIN LATERAL (SELECT id FROM ai_candidate_snapshot WHERE job_id = j.id
                        ORDER BY snapshot_version DESC, created_at DESC, id DESC LIMIT 1) s ON true
                    JOIN ai_registration_unit u ON u.snapshot_id = s.id
                    GROUP BY j.source, j.model_version, j.prompt_version, j.schema_version,
                             u.review_status, u.block_reason, 8
                ) SELECT * FROM measures ORDER BY source, model_version, prompt_version, schema_version, kind, status
                """;
        Accumulator total = new Accumulator();
        Map<Dimension, Accumulator> groups = new LinkedHashMap<>();
        jdbc.query(sql, rs -> {
            Dimension dimension = new Dimension(rs.getString("source"), rs.getString("model_version"),
                    rs.getString("prompt_version"), rs.getString("schema_version"));
            Accumulator group = groups.computeIfAbsent(dimension, ignored -> new Accumulator());
            String kind = rs.getString("kind");
            String status = rs.getString("status");
            String reason = rs.getString("reason");
            boolean registered = rs.getBoolean("registered");
            long count = rs.getLong("n");
            total.add(kind, status, reason, registered, count);
            group.add(kind, status, reason, registered, count);
        }, args.toArray());
        return new Statistics(total.counts(), groups.entrySet().stream().map(entry -> {
            Dimension d = entry.getKey();
            return new Group(d.source(), d.model(), d.prompt(), d.schema(), entry.getValue().counts());
        }).toList());
    }

    private void addFilter(StringBuilder where, List<Object> args, String column, String value) {
        if (value != null) {
            where.append(" AND ").append(column).append(" = ?");
            args.add(value);
        }
    }

    private record Dimension(String source, String model, String prompt, String schema) { }

    private static final class Accumulator {
        private final Map<String, Long> jobs = new LinkedHashMap<>();
        private final Map<String, Long> units = new LinkedHashMap<>();
        private final Map<String, Long> reasons = new LinkedHashMap<>();
        private long registered;

        private Accumulator() {
            List.of("QUEUED", "RUNNING", "SUCCEEDED", "FAILED").forEach(status -> jobs.put(status, 0L));
            List.of("AUTO_CONFIRMED", "AUTO_BLOCKED", "AUTO_REJECTED", "MANUAL_OVERRIDE")
                    .forEach(status -> units.put(status, 0L));
        }

        private void add(String kind, String status, String reason, boolean isRegistered, long count) {
            if ("JOB".equals(kind)) {
                jobs.merge(status, count, Long::sum);
            } else {
                units.merge(status, count, Long::sum);
                if (isRegistered) registered += count;
                if ("AUTO_BLOCKED".equals(status) && reason != null) reasons.merge(reason, count, Long::sum);
            }
        }

        private Counts counts() {
            long confirmed = units.get("AUTO_CONFIRMED");
            long blocked = units.get("AUTO_BLOCKED");
            long rejected = units.get("AUTO_REJECTED");
            long evaluated = confirmed + blocked + rejected;
            Map<String, Reason> reasonCounts = new LinkedHashMap<>();
            reasons.forEach((reason, count) -> reasonCounts.put(reason, new Reason(count, ratio(count, blocked))));
            return new Counts(jobs.values().stream().mapToLong(Long::longValue).sum(), jobs,
                    units.values().stream().mapToLong(Long::longValue).sum(), evaluated,
                    confirmed, blocked, rejected, units.get("MANUAL_OVERRIDE"), registered,
                    ratio(blocked, evaluated), reasonCounts);
        }

        private Double ratio(long numerator, long denominator) {
            return denominator == 0 ? null : (double)numerator / denominator;
        }
    }
}
