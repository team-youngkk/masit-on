package com.masiton.visit.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.masiton.visit.application.port.out.VisitRepositoryPort;
import com.masiton.visit.domain.model.Visit;

/**
 * VisitRepositoryPort의 JPA 구현체다. JPA Entity와 도메인 모델 변환은 VisitMapper에 위임한다.
 */
@Component
class VisitPersistenceAdapter implements VisitRepositoryPort {

    private final SpringDataVisitRepository springDataVisitRepository;
    private final JdbcTemplate jdbcTemplate;

    VisitPersistenceAdapter(SpringDataVisitRepository springDataVisitRepository, JdbcTemplate jdbcTemplate) {
        this.springDataVisitRepository = springDataVisitRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Visit save(Visit visit) {
        VisitJpaEntity savedEntity = springDataVisitRepository.save(VisitMapper.toEntity(visit));
        return VisitMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Visit> findById(UUID id) {
        return springDataVisitRepository.findById(id)
                .map(VisitMapper::toDomain);
    }

    @Override
    public Optional<Visit> findByRestaurantIdAndCreatorIdAndVideoId(
            UUID restaurantId,
            UUID creatorId,
            UUID videoId
    ) {
        return springDataVisitRepository.findByRestaurantIdAndCreatorIdAndVideoId(restaurantId, creatorId, videoId)
                .map(VisitMapper::toDomain);
    }

    @Override
    public Optional<Visit> insertIfAbsent(Visit visit) {
        UUID id = jdbcTemplate.query(
                """
                insert into visit (id, restaurant_id, creator_id, video_id, publication_status, lifecycle_status)
                values (?, ?, ?, ?, ?, ?)
                on conflict (restaurant_id, creator_id, video_id) do nothing
                returning id
                """,
                resultSet -> resultSet.next() ? resultSet.getObject("id", UUID.class) : null,
                visit.getId(),
                visit.getRestaurantId(),
                visit.getCreatorId(),
                visit.getVideoId(),
                visit.getPublicationStatus().name(),
                visit.getLifecycleStatus().name());
        return id == null ? Optional.empty() : findById(id);
    }
}
