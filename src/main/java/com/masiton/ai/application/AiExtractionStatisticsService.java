package com.masiton.ai.application;

import java.time.Duration;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.ai.application.port.out.AiExtractionStatisticsPort;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.common.web.SafeTextPolicy;

@Service
public class AiExtractionStatisticsService {
    private final AiExtractionStatisticsPort port;

    public AiExtractionStatisticsService(AiExtractionStatisticsPort port) {
        this.port = port;
    }

    @Transactional(readOnly = true)
    public AiExtractionStatisticsPort.Statistics query(AiExtractionStatisticsPort.Filter filter) {
        if (filter.from() == null || filter.to() == null
                || !filter.from().toInstant().isBefore(filter.to().toInstant())
                || Duration.between(filter.from().toInstant(), filter.to().toInstant()).compareTo(Duration.ofDays(365)) > 0) {
            throw invalid("from/to");
        }
        if (filter.source() != null && !Set.of("ADMIN", "WEBHOOK").contains(filter.source())) {
            throw invalid("source");
        }
        validateVersion(filter.modelVersion(), 128, "modelVersion");
        validateVersion(filter.promptVersion(), 64, "promptVersion");
        validateVersion(filter.schemaVersion(), 64, "schemaVersion");
        return port.query(filter);
    }

    private void validateVersion(String value, int maxLength, String field) {
        if (value != null) SafeTextPolicy.requireSafe(value, field);
        if (value != null && (value.codePoints().anyMatch(Character::isISOControl) || value.isBlank() || value.length() > maxLength)) {
            throw invalid(field);
        }
    }

    private BusinessException invalid(String field) {
        return new BusinessException(ErrorCode.INVALID_FIELD_VALUE, field, "Invalid statistics filter.");
    }
}
