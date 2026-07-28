package com.masiton.creator.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.masiton.creator.application.port.out.CreatorRepositoryPort;
import com.masiton.creator.domain.model.Creator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ConstraintViolationIntegrationTest 패턴을 따른다. 각 테스트는 고유 UUID로 자신의 데이터만
 * 준비하고, 같은 컨테이너를 공유하는 다른 테스트의 데이터가 섞여도 자신이 만든 행만 걸러서 검증한다.
 */
@SpringBootTest
@Testcontainers
@DisplayName("Creator 공개 선택 목록 저장소 조회")
class CreatorPersistenceAdapterIntegrationTest {

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
    private CreatorRepositoryPort creatorRepositoryPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("공개·활성 Creator 여러 건을 채널명 오름차순으로 반환한다")
    void 조회_공개Creator여러건_채널명오름차순으로반환한다() {
        // given
        String suffix = UUID.randomUUID().toString();
        UUID firstId = UUID.randomUUID();
        UUID lastId = UUID.randomUUID();
        String firstName = "가" + suffix;
        String lastName = "하" + suffix;
        insertPublicActiveCreator(lastId, lastName);
        insertPublicActiveCreator(firstId, firstName);

        // when
        List<Creator> result = creatorRepositoryPort.findPublicSelectionList();

        // then
        List<UUID> orderedIds = result.stream()
                .filter(creator -> creator.getId().equals(firstId) || creator.getId().equals(lastId))
                .map(Creator::getId)
                .toList();
        assertThat(orderedIds).containsExactly(firstId, lastId);
    }

    @Test
    @DisplayName("비공개(PRIVATE) Creator는 선택 목록에서 제외한다")
    void 조회_비공개Creator포함_비공개Creator는목록에서제외한다() {
        // given
        UUID privateId = UUID.randomUUID();
        insertCreator(privateId, "비공개채널" + UUID.randomUUID(), "PRIVATE", "ACTIVE", null);

        // when
        List<Creator> result = creatorRepositoryPort.findPublicSelectionList();

        // then
        assertThat(result).extracting(Creator::getId).doesNotContain(privateId);
    }

    @Test
    @DisplayName("삭제(DELETED) Creator는 선택 목록에서 제외한다")
    void 조회_삭제Creator포함_삭제Creator는목록에서제외한다() {
        // given
        UUID deletedId = UUID.randomUUID();
        insertCreator(deletedId, "삭제채널" + UUID.randomUUID(), "PRIVATE", "DELETED", OffsetDateTime.now());

        // when
        List<Creator> result = creatorRepositoryPort.findPublicSelectionList();

        // then
        assertThat(result).extracting(Creator::getId).doesNotContain(deletedId);
    }

    @Test
    @DisplayName("채널명이 같으면 id 오름차순으로 반환한다")
    void 조회_채널명이동일한Creator여러건_id오름차순으로반환한다() {
        // given
        String sameName = "동일채널" + UUID.randomUUID();
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        insertPublicActiveCreator(idA, sameName);
        insertPublicActiveCreator(idB, sameName);
        List<UUID> expectedOrder = jdbcTemplate.query(
                "SELECT id FROM creator WHERE id IN (?, ?) ORDER BY id",
                (rs, rowNum) -> (UUID) rs.getObject("id"),
                idA,
                idB);

        // when
        List<Creator> result = creatorRepositoryPort.findPublicSelectionList();

        // then
        List<UUID> orderedIds = result.stream()
                .filter(creator -> sameName.equals(creator.getChannelName()))
                .map(Creator::getId)
                .toList();
        assertThat(orderedIds).containsExactlyElementsOf(expectedOrder);
    }

    private void insertPublicActiveCreator(UUID id, String channelName) {
        insertCreator(id, channelName, "PUBLIC", "ACTIVE", null);
    }

    private void insertCreator(
            UUID id,
            String channelName,
            String publicationStatus,
            String lifecycleStatus,
            OffsetDateTime deletedAt) {
        jdbcTemplate.update(
                "INSERT INTO creator "
                        + "(id, external_channel_id, channel_name, channel_url, publication_status, "
                        + "lifecycle_status, external_status_checked_at, deleted_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                "UC-" + UUID.randomUUID(),
                channelName,
                "https://example.com/channel/" + id,
                publicationStatus,
                lifecycleStatus,
                OffsetDateTime.now(),
                deletedAt);
    }
}
