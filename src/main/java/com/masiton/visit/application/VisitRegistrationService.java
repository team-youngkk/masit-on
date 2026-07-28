package com.masiton.visit.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.common.web.BusinessException;
import com.masiton.visit.application.port.in.RegisterVisitUseCase;
import com.masiton.visit.application.port.out.VisitRepositoryPort;
import com.masiton.visit.domain.model.Visit;
import com.masiton.visit.domain.model.VisitEvidenceRequiredException;

/**
 * Visit 조합의 일반 중복 선조회와 DB UNIQUE 경쟁 조건을 함께 다룬다.
 * 외부 참조 검증은 교차 도메인 책임이므로 orchestration에서 끝낸 뒤 이 Port를 호출한다.
 */
@Service
class VisitRegistrationService implements RegisterVisitUseCase {

    private final VisitRepositoryPort visitRepository;

    VisitRegistrationService(VisitRepositoryPort visitRepository) {
        this.visitRepository = visitRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public VisitRegistrationResult register(RegisterVisitCommand command) {
        Visit visit = createVisit(command);
        return visitRepository.findByRestaurantIdAndCreatorIdAndVideoId(
                        command.restaurantId(), command.creatorId(), command.videoId())
                .map(existing -> new VisitRegistrationResult(existing.getId(), false))
                .orElseGet(() -> insert(visit));
    }

    private Visit createVisit(RegisterVisitCommand command) {
        try {
            return Visit.register(
                    UUID.randomUUID(),
                    command.restaurantId(),
                    command.creatorId(),
                    command.videoId(),
                    command.visitEvidenceConfirmed());
        } catch (VisitEvidenceRequiredException exception) {
            throw new BusinessException(
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "VISIT_EVIDENCE_INSUFFICIENT",
                    "방문 근거 확인이 필요합니다.");
        }
    }

    private VisitRegistrationResult insert(Visit visit) {
        return visitRepository.insertIfAbsent(visit)
                .map(saved -> new VisitRegistrationResult(saved.getId(), true))
                .orElseGet(() -> new VisitRegistrationResult(null, false));
    }
}
