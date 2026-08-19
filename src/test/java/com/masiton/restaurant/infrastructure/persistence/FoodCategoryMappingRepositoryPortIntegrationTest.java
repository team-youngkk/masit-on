package com.masiton.restaurant.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.masiton.restaurant.application.port.out.FoodCategoryMappingRepositoryPort;
import com.masiton.restaurant.application.port.out.FoodCategoryRepositoryPort;
import com.masiton.restaurant.domain.model.FoodCategoryMapping;
import com.masiton.restaurant.domain.model.FoodCategoryMappingMatchType;
import com.masiton.restaurant.domain.model.FoodCategoryMappingSourceType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V8 마이그레이션이 적재한 food_category_mapping seed와 대조 순서(EXACT 우선, priority 오름차순)를
 * 실제 PostgreSQL로 검증한다.
 */
@SpringBootTest
@com.masiton.test.TestProfile
@Testcontainers
@DisplayName("FoodCategoryMapping 대조 순서 조회")
class FoodCategoryMappingRepositoryPortIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17.10-alpine")
                    .withDatabaseName("masiton")
                    .withUsername("masiton")
                    .withPassword("masiton_local");

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private FoodCategoryMappingRepositoryPort mappingRepositoryPort;

    @Autowired
    private FoodCategoryRepositoryPort foodCategoryRepositoryPort;

    @Test
    @DisplayName("MENU_EXPRESSION 조회는 seed의 '한식' EXACT 매핑을 포함한다")
    void MENU_EXPRESSION조회_seed의한식EXACT매핑을포함한다() {
        List<FoodCategoryMapping> rows = mappingRepositoryPort
                .findActiveBySourceTypeOrderByMatchTypeThenPriority(FoodCategoryMappingSourceType.MENU_EXPRESSION);

        assertThat(rows).isNotEmpty();
        FoodCategoryMapping hansik = rows.stream()
                .filter(row -> row.getPattern().equals("한식"))
                .findFirst()
                .orElseThrow();
        assertThat(hansik.getMatchType()).isEqualTo(FoodCategoryMappingMatchType.EXACT);
        assertThat(hansik.isActive()).isTrue();
        assertThat(foodCategoryRepositoryPort.findById(hansik.getFoodCategoryId()))
                .hasValueSatisfying(category -> assertThat(category.getName()).isEqualTo("한식"));
    }

    @Test
    @DisplayName("KAKAO_PLACE_CATEGORY 조회는 seed 행만 있으면 모두 PARTIAL이다")
    void KAKAO_PLACE_CATEGORY조회_seed행은모두PARTIAL이다() {
        List<FoodCategoryMapping> rows = mappingRepositoryPort
                .findActiveBySourceTypeOrderByMatchTypeThenPriority(FoodCategoryMappingSourceType.KAKAO_PLACE_CATEGORY);

        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row -> assertThat(row.getMatchType())
                .isEqualTo(FoodCategoryMappingMatchType.PARTIAL));
    }

    @Test
    @DisplayName("같은 source_type에서 EXACT 행이 PARTIAL 행보다 항상 먼저 온다")
    void 조회_EXACT행이PARTIAL행보다먼저온다() {
        UUID hansikCategoryId = foodCategoryRepositoryPort.findByName("한식").orElseThrow().getId();
        OffsetDateTime now = OffsetDateTime.now();
        mappingRepositoryPort.save(new FoodCategoryMapping(
                UUID.randomUUID(), FoodCategoryMappingSourceType.MENU_EXPRESSION, "삼계탕",
                FoodCategoryMappingMatchType.PARTIAL, hansikCategoryId, (short) 1, true, now, now));

        List<FoodCategoryMapping> rows = mappingRepositoryPort
                .findActiveBySourceTypeOrderByMatchTypeThenPriority(FoodCategoryMappingSourceType.MENU_EXPRESSION);

        int firstPartialIndex = -1;
        int lastExactIndex = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getMatchType() == FoodCategoryMappingMatchType.PARTIAL && firstPartialIndex == -1) {
                firstPartialIndex = i;
            }
            if (rows.get(i).getMatchType() == FoodCategoryMappingMatchType.EXACT) {
                lastExactIndex = i;
            }
        }
        assertThat(firstPartialIndex).isGreaterThan(lastExactIndex);
    }

    @Test
    @DisplayName("존재하지 않는 조합이어도 예외 없이 빈 목록을 반환한다")
    void 조회_비활성화후_빈목록을반환할수있다() {
        UUID hansikCategoryId = foodCategoryRepositoryPort.findByName("한식").orElseThrow().getId();
        OffsetDateTime now = OffsetDateTime.now();
        FoodCategoryMapping saved = mappingRepositoryPort.save(new FoodCategoryMapping(
                UUID.randomUUID(), FoodCategoryMappingSourceType.MENU_EXPRESSION, "temp-inactive-pattern",
                FoodCategoryMappingMatchType.EXACT, hansikCategoryId, (short) 99, false, now, now));

        List<FoodCategoryMapping> rows = mappingRepositoryPort
                .findActiveBySourceTypeOrderByMatchTypeThenPriority(FoodCategoryMappingSourceType.MENU_EXPRESSION);

        assertThat(rows).noneMatch(row -> row.getId().equals(saved.getId()));
    }
}
