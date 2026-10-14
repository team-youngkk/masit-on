package com.masiton.restaurant.application.port.in;

import java.util.List;

public interface GetRegionHierarchyUseCase {

    List<RegionHierarchyView> getHierarchy();
}
