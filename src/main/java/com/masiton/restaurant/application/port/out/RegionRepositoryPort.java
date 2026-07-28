package com.masiton.restaurant.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.masiton.restaurant.domain.model.Region;

/**
 * Region 저장소에 대한 Application 출력 Port다.
 * Application은 이 인터페이스에만 의존하고 Infrastructure Adapter가 구현한다.
 */
public interface RegionRepositoryPort {

    Region save(Region region);

    Optional<Region> findById(UUID id);

    Optional<Region> findByName(String name);
}
