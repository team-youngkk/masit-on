package com.masiton.ai.infrastructure.worker;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AI 추출 Worker 스케줄러")
class AiExtractionWorkerSchedulerTest {

    @Test
    @DisplayName("AI polling은 애플리케이션 전역 스케줄러와 분리한다")
    void polling_AI전용스케줄러를사용한다() throws Exception {
        Method poll = AiExtractionWorkerScheduler.class.getMethod("poll");

        assertThat(poll.getAnnotation(Scheduled.class).scheduler()).isEqualTo("aiWorkerTaskScheduler");

        ThreadPoolTaskScheduler scheduler = new AiExtractionWorkerConfiguration().aiWorkerTaskScheduler();
        assertThat(scheduler.getThreadNamePrefix()).isEqualTo("ai-worker-poll-");
    }
}
