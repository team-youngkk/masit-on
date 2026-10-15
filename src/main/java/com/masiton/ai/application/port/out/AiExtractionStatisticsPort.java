package com.masiton.ai.application.port.out;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public interface AiExtractionStatisticsPort {
    Statistics query(Filter filter);

    record Filter(OffsetDateTime from, OffsetDateTime to, String source, String modelVersion,
                  String promptVersion, String schemaVersion) { }

    record Counts(long totalJobs, Map<String, Long> jobStatuses, long totalUnits, long evaluatedUnits,
                  long confirmedUnits, long blockedUnits, long rejectedUnits, long manualOverrideUnits,
                  long registeredUnits, Double blockedRate, Map<String, Reason> blockReasons) { }

    record Reason(long count, Double blockedShare) { }

    record Group(String source, String modelVersion, String promptVersion, String schemaVersion, Counts counts) { }

    record Statistics(Counts totals, List<Group> groups) { }
}
