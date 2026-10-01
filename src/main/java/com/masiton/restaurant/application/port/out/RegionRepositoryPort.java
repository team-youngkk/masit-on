package com.masiton.restaurant.application.port.out;

import java.util.List;
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

    /** 기존 district 필터와의 호환을 위해 서울 자치구만 조회한다. */
    Optional<Region> findByName(String name);

    Optional<Region> findByAdministrativeCode(String code);

    List<Region> findAllActive();

    Optional<Region> findByProvinceAndName(String province, String name);
}
