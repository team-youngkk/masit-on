package com.masiton.video.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.masiton.video.application.port.out.VideoRepositoryPort;
import com.masiton.video.domain.model.Video;

/**
 * VideoRepositoryPort의 Spring Data JPA 기반 구현체다.
 */
@Component
class VideoPersistenceAdapter implements VideoRepositoryPort {

    private final SpringDataVideoRepository springDataVideoRepository;

    VideoPersistenceAdapter(SpringDataVideoRepository springDataVideoRepository) {
        this.springDataVideoRepository = springDataVideoRepository;
    }

    @Override
    public Video save(Video video) {
        VideoJpaEntity savedEntity = springDataVideoRepository.save(VideoMapper.toEntity(video));
        return VideoMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Video> findById(UUID id) {
        return springDataVideoRepository.findById(id)
                .map(VideoMapper::toDomain);
    }
}
