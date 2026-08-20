package com.masiton.restaurant.infrastructure.persistence;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.masiton.restaurant.application.port.out.FoodCategoryRepositoryPort;
import com.masiton.restaurant.application.port.out.RegionRepositoryPort;
import com.masiton.restaurant.domain.model.FoodCategory;
import com.masiton.restaurant.domain.model.Region;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RestaurantSearchQueryService가 district·category 이름으로 Region·FoodCategory를 조회하는
 * findByName의 실제 PostgreSQL 동작을 검증한다. 초기 스키마 baseline이 적재한 서울 자치구·대표 음식 카테고리
 * 기준 데이터를 그대로 사용한다.
 */
import com.masiton.test.FullContextIntegrationTest;

@SpringBootTest
@DisplayName("Region·FoodCategory 이름 조회")
class ReferenceDataLookupIntegrationTest extends FullContextIntegrationTest {

    @Autowired
    private RegionRepositoryPort regionRepositoryPort;

    @Autowired
    private FoodCategoryRepositoryPort foodCategoryRepositoryPort;

    @Test
    @DisplayName("등록된 자치구 이름으로 조회하면 해당 Region을 반환한다")
    void Region조회_등록된자치구이름_해당Region을반환한다() {
        // given: baseline이 '마포구'를 적재했다.

        // when
        Optional<Region> region = regionRepositoryPort.findByName("마포구");

        // then
        assertThat(region).isPresent();
        assertThat(region.get().getName()).isEqualTo("마포구");
        assertThat(region.get().isActive()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 자치구 이름으로 조회하면 빈 값을 반환한다")
    void Region조회_존재하지않는이름_빈값을반환한다() {
        // given: '없는구'는 기준 데이터에 없다.

        // when
        Optional<Region> region = regionRepositoryPort.findByName("없는구");

        // then
        assertThat(region).isEmpty();
    }

    @Test
    @DisplayName("등록된 대표 음식 카테고리 이름으로 조회하면 해당 FoodCategory를 반환한다")
    void FoodCategory조회_등록된카테고리이름_해당FoodCategory를반환한다() {
        // given: baseline이 '한식'을 적재했다.

        // when
        Optional<FoodCategory> foodCategory = foodCategoryRepositoryPort.findByName("한식");

        // then
        assertThat(foodCategory).isPresent();
        assertThat(foodCategory.get().getName()).isEqualTo("한식");
        assertThat(foodCategory.get().isActive()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 카테고리 이름으로 조회하면 빈 값을 반환한다")
    void FoodCategory조회_존재하지않는이름_빈값을반환한다() {
        // given: '없는음식'은 기준 데이터에 없다.

        // when
        Optional<FoodCategory> foodCategory = foodCategoryRepositoryPort.findByName("없는음식");

        // then
        assertThat(foodCategory).isEmpty();
    }
}
