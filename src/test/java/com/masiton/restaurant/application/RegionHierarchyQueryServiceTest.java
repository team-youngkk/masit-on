package com.masiton.restaurant.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.restaurant.application.port.in.RegionHierarchyView;
import com.masiton.restaurant.application.port.out.RegionRepositoryPort;
import com.masiton.restaurant.application.query.RegionHierarchyQueryService;
import com.masiton.restaurant.domain.model.Region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("지역 계층 조회 서비스")
class RegionHierarchyQueryServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-30T00:00:00Z");

    @Test
    @DisplayName("활성 시도는 활성 자식만 코드 순서로 묶어 반환하고 비활성 부모의 자식은 제외한다")
    void 계층조회_활성시도와자식_비활성부모자식제외() {
        RegionRepositoryPort regions = mock(RegionRepositoryPort.class);
        UUID seoulId = UUID.randomUUID();
        UUID inactiveParentId = UUID.randomUUID();
        Region seoul = region(seoulId, "1100000000", "서울특별시", null, true, 1);
        Region mapo = region(UUID.randomUUID(), "1144000000", "마포구", seoulId, true, 14);
        Region inactiveChild = region(UUID.randomUUID(), "1111000000", "종로구", seoulId, false, 1);
        Region orphanChild = region(UUID.randomUUID(), "2611000000", "중구", inactiveParentId, true, 1);
        when(regions.findAllActive()).thenReturn(List.of(orphanChild, inactiveChild, mapo, seoul));

        List<RegionHierarchyView> result = new RegionHierarchyQueryService(regions).getHierarchy();

        assertThat(result).extracting(RegionHierarchyView::code).containsExactly("1100000000");
        assertThat(result.get(0).children()).extracting(RegionHierarchyView.Child::code)
                .containsExactly("1144000000");
    }

    @Test
    @DisplayName("활성 마스터가 없으면 지역 계층은 빈 목록으로 반환한다")
    void 계층조회_활성마스터없음_빈목록() {
        RegionRepositoryPort regions = mock(RegionRepositoryPort.class);
        when(regions.findAllActive()).thenReturn(List.of());

        assertThat(new RegionHierarchyQueryService(regions).getHierarchy()).isEmpty();
    }

    private Region region(UUID id, String code, String name, UUID parentId, boolean active, int sortOrder) {
        return new Region(id, "KR_" + code, name, (short) sortOrder, active, NOW, NOW, code, parentId);
    }
}
