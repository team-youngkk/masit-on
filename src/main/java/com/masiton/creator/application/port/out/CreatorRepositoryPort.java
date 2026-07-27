package com.masiton.creator.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.masiton.creator.domain.model.Creator;

public interface CreatorRepositoryPort {

    Creator save(Creator creator);

    Optional<Creator> findById(UUID id);
}
