package com.masiton.creator.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.masiton.creator.application.port.out.CreatorRepositoryPort;
import com.masiton.creator.domain.model.Creator;

@Component
class CreatorPersistenceAdapter implements CreatorRepositoryPort {

    private final SpringDataCreatorRepository springDataCreatorRepository;
    private final JdbcTemplate jdbcTemplate;

    public CreatorPersistenceAdapter(
            SpringDataCreatorRepository springDataCreatorRepository,
            JdbcTemplate jdbcTemplate) {
        this.springDataCreatorRepository = springDataCreatorRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Creator save(Creator creator) {
        CreatorJpaEntity savedEntity = springDataCreatorRepository.save(CreatorMapper.toEntity(creator));
        return CreatorMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Creator> findById(UUID id) {
        return springDataCreatorRepository.findById(id).map(CreatorMapper::toDomain);
    }

    @Override
    public Optional<Creator> findByExternalChannelId(String externalChannelId) {
        return springDataCreatorRepository.findByExternalChannelId(externalChannelId).map(CreatorMapper::toDomain);
    }

    @Override
    public Optional<Creator> insertIfAbsent(Creator creator) {
        UUID id = jdbcTemplate.query("""
                        insert into creator (
                            id, external_channel_id, channel_name, channel_url, publication_status,
                            lifecycle_status, external_availability_status, external_status_checked_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        on conflict (external_channel_id) do nothing
                        returning id
                        """,
                resultSet -> resultSet.next() ? resultSet.getObject("id", UUID.class) : null,
                creator.getId(), creator.getExternalChannelId(), creator.getChannelName(), creator.getChannelUrl(),
                creator.getPublicationStatus().name(), creator.getLifecycleStatus().name(),
                creator.getExternalAvailabilityStatus().name(), creator.getExternalStatusCheckedAt());
        return id == null ? Optional.empty() : findById(id);
    }
}
