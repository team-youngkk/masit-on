package com.masiton.ai.application;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.ai.application.port.out.AiExtractionJobStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AI 임시 입력 cleanup 서비스")
class AiTemporaryInputCleanupServiceTest {

    private final AiExtractionJobStore store = mock(AiExtractionJobStore.class);
    private final AiTemporaryInputCleanupService service = new AiTemporaryInputCleanupService(store);

    @Test
    @DisplayName("만료 임시 입력 삭제를 영속성 Port에 위임한다")
    void deleteExpiredInputs_만료입력_영속성Port에위임한다() {
        when(store.deleteExpiredTemporaryInputs(any(OffsetDateTime.class))).thenReturn(2);

        assertThat(service.deleteExpiredInputs()).isEqualTo(2);

        verify(store).deleteExpiredTemporaryInputs(any(OffsetDateTime.class));
    }
}
