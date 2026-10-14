package com.masiton.restaurant.application;

import java.util.Optional;

import com.masiton.common.address.RoadAddressNormalizer;
import com.masiton.restaurant.application.port.out.RegionRepositoryPort;
import com.masiton.restaurant.domain.model.Region;

/** 수동 등록과 AI 등록이 동일한 활성 지역 마스터를 사용하도록 주소 해석을 모은다. */
final class RegionAddressResolver {

    private final RegionRepositoryPort regions;

    RegionAddressResolver(RegionRepositoryPort regions) {
        this.regions = regions;
    }

    Optional<Region> resolve(String address) {
        return RoadAddressNormalizer.extractRegion(address)
                .flatMap(region -> "세종특별자치시".equals(region.province())
                        ? regions.findByAdministrativeCode("3611000000")
                        : regions.findByProvinceAndName(region.province(), region.municipality()))
                .filter(Region::isActive);
    }
}
