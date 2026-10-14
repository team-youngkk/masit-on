package com.masiton.restaurant.application.query;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.restaurant.application.port.in.GetRegionHierarchyUseCase;
import com.masiton.restaurant.application.port.in.RegionHierarchyView;
import com.masiton.restaurant.application.port.out.RegionRepositoryPort;
import com.masiton.restaurant.domain.model.Region;

@Service
@Transactional(readOnly = true)
public class RegionHierarchyQueryService implements GetRegionHierarchyUseCase {

    private static final Comparator<Region> ORDER = Comparator.comparingInt(Region::getSortOrder)
            .thenComparing(Region::getAdministrativeCode);

    private final RegionRepositoryPort regions;

    public RegionHierarchyQueryService(RegionRepositoryPort regions) {
        this.regions = regions;
    }

    @Override
    public List<RegionHierarchyView> getHierarchy() {
        List<Region> active = regions.findAllActive().stream().filter(Region::isActive).sorted(ORDER).toList();
        var children = active.stream().filter(region -> region.getParentId() != null)
                .collect(Collectors.groupingBy(Region::getParentId));
        return active.stream().filter(region -> region.getParentId() == null)
                .map(province -> new RegionHierarchyView(province.getAdministrativeCode(), province.getName(),
                        children.getOrDefault(province.getId(), List.of()).stream()
                                .map(child -> new RegionHierarchyView.Child(
                                        child.getAdministrativeCode(), child.getName()))
                                .toList()))
                .toList();
    }
}
