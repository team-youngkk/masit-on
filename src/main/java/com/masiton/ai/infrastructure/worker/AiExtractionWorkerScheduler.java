package com.masiton.ai.infrastructure.worker;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.masiton.ai.application.AiExtractionWorkerService;

@Component
public class AiExtractionWorkerScheduler {

    private final AiExtractionWorkerService worker;

    public AiExtractionWorkerScheduler(AiExtractionWorkerService worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${masiton.ai.worker.poll-interval:PT5S}")
    public void poll() {
        worker.poll();
    }
}
