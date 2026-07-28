package com.masiton.visit.application;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.common.web.BusinessException;
import com.masiton.visit.application.port.in.RegisterVisitUseCase;
import com.masiton.visit.application.port.out.VisitRepositoryPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("Visit 등록 Application 서비스")
class VisitRegistrationServiceTest {

    private final VisitRepositoryPort visitRepository = mock(VisitRepositoryPort.class);
    private final VisitRegistrationService service = new VisitRegistrationService(visitRepository);

    @Test
    @DisplayName("방문 근거가 확인되지 않으면 직접 호출도 저장소 조회 없이 422를 반환한다")
    void register_방문근거미확인_저장소미조회422반환() {
        RegisterVisitUseCase.RegisterVisitCommand command = new RegisterVisitUseCase.RegisterVisitCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), false);

        assertThatThrownBy(() -> service.register(command))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("VISIT_EVIDENCE_INSUFFICIENT"));
        verifyNoInteractions(visitRepository);
    }
}
