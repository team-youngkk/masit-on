package com.masiton;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("애플리케이션 컨텍스트")
class MasitOnApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("의존 서비스에 연결하지 않고도 컨텍스트가 기동한다")
    void 컨텍스트기동_의존서비스없음_성공() {
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.getBean(MasitOnApplication.class)).isNotNull();
    }
}
