package com.masiton.ai.presentation;

import java.time.OffsetDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.ai.application.AiExtractionStatisticsService;
import com.masiton.ai.application.port.out.AiExtractionStatisticsPort;

@RestController
public class AdminAiExtractionStatisticsController {
    private final AiExtractionStatisticsService service;

    public AdminAiExtractionStatisticsController(AiExtractionStatisticsService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/ai/video-extractions/statistics")
    public AiExtractionStatisticsPort.Statistics statistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String modelVersion,
            @RequestParam(required = false) String promptVersion,
            @RequestParam(required = false) String schemaVersion) {
        return service.query(new AiExtractionStatisticsPort.Filter(from, to, source, modelVersion, promptVersion, schemaVersion));
    }
}
