package com.masiton.ai.infrastructure.worker;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.masiton.test.FullContextIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("AI 추출 Worker 스케줄러 통합")
class AiExtractionWorkerSchedulerIntegrationTest extends FullContextIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("AI polling과 애플리케이션 예약 작업은 서로 다른 스케줄러를 사용한다")
    void 컨텍스트기동_AI와기본스케줄러_분리() {
        Map<String, TaskScheduler> schedulers = applicationContext.getBeansOfType(TaskScheduler.class);

        assertThat(schedulers).containsKeys("taskScheduler", "aiWorkerTaskScheduler");
        assertThat(schedulers.get("taskScheduler"))
                .isNotSameAs(schedulers.get("aiWorkerTaskScheduler"))
                .isInstanceOf(ThreadPoolTaskScheduler.class);
        assertThat(schedulers.get("aiWorkerTaskScheduler"))
                .isInstanceOfSatisfying(ThreadPoolTaskScheduler.class,
                        scheduler -> assertThat(scheduler.getThreadNamePrefix())
                                .isEqualTo("ai-worker-poll-"));
    }
}
