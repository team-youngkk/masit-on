package com.masiton.visit.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.masiton.visit.domain.model.Visit;

/**
 * Application이 Visit 영속성에 요구하는 계약이다.
 * Infrastructure Adapter가 구현하며, Application은 이 인터페이스만 의존한다.
 */
public interface VisitRepositoryPort {

    Visit save(Visit visit);

    Optional<Visit> findById(UUID id);
}
