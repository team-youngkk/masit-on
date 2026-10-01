package com.masiton.restaurant.application;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.restaurant.application.port.out.RegionRepositoryPort;
import com.masiton.restaurant.domain.model.Region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("주소 기반 활성 지역 매핑")
class RegionAddressResolverTest {
    private final RegionRepositoryPort regions = mock(RegionRepositoryPort.class);
    private final RegionAddressResolver resolver = new RegionAddressResolver(regions);

    @Test
    @DisplayName("같은 중구라도 시도가 다르면 서로 다른 마스터를 조회한다")
    void 매핑_동명지역_시도별로구분한다() {
        // Given
        Region seoul = region("중구", true);
        Region busan = region("중구", true);
        when(regions.findByProvinceAndName("서울특별시", "중구")).thenReturn(Optional.of(seoul));
        when(regions.findByProvinceAndName("부산광역시", "중구")).thenReturn(Optional.of(busan));
        // When / Then
        assertThat(resolver.resolve("서울 중구 세종대로 1")).contains(seoul);
        assertThat(resolver.resolve("부산 중구 중앙대로 1")).contains(busan);
        verify(regions, never()).findByName(anyString());
    }

    @Test
    @DisplayName("일반시의 비자치구는 시 단위로 연결한다")
    void 매핑_수원시영통구_수원시로연결한다() {
        // Given
        Region suwon = region("수원시", true);
        when(regions.findByProvinceAndName("경기도", "수원시")).thenReturn(Optional.of(suwon));
        // When / Then
        assertThat(resolver.resolve("경기 수원시 영통구 광교로 1")).contains(suwon);
    }

    @Test
    @DisplayName("세종은 가상 시군구 없이 시도 마스터에 직접 연결한다")
    void 매핑_세종주소_시도로연결한다() {
        // Given
        Region sejong = region("세종특별자치시", true);
        when(regions.findByAdministrativeCode("3611000000")).thenReturn(Optional.of(sejong));
        // When / Then
        assertThat(resolver.resolve("세종 한누리대로 1")).contains(sejong);
        verify(regions, never()).findByProvinceAndName(anyString(), anyString());
    }

    @Test
    @DisplayName("미등록·비활성 지역과 불완전한 주소에는 지역을 부여하지 않는다")
    void 매핑_유효마스터없음_빈값을반환한다() {
        // Given
        when(regions.findByProvinceAndName("제주특별자치도", "서귀포시"))
                .thenReturn(Optional.of(region("서귀포시", false)));
        // When / Then
        assertThat(resolver.resolve("제주 서귀포시 중문로 1")).isEmpty();
        assertThat(resolver.resolve("부산 없는구 도로 1")).isEmpty();
        assertThat(resolver.resolve("부산광역시")).isEmpty();
    }

    private Region region(String name, boolean active) {
        return new Region(UUID.randomUUID(), "TEST", name, (short) 1, active, null, null);
    }
}
