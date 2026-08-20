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
import com.masiton.creator.domain.model.ExternalAvailabilityStatus;
import com.masiton.creator.domain.model.LifecycleStatus;
import com.masiton.creator.domain.model.PublicationStatus;

import static org.assertj.core.api.Assertions.assertThat;

import com.masiton.test.FullContextIntegrationTest;

/**
 * ConstraintViolationIntegrationTest 패턴을 따른다. 각 테스트는 고유 UUID로 자신의 데이터만
 * 준비하고, 같은 컨테이너를 공유하는 다른 테스트의 데이터가 섞여도 자신이 만든 행만 걸러서 검증한다.
 */
@SpringBootTest
@DisplayName("Creator 공개 선택 목록 저장소 조회")
class CreatorPersistenceAdapterIntegrationTest extends FullContextIntegrationTest {

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

    @Test
    @DisplayName("insertIfAbsent는 프로필 이미지·소개·handle을 그대로 저장한다")
    void 저장_insertIfAbsent_표시정보세필드를그대로저장한다() {
        // given: CreatorRegistrationService.create()가 실제로 쓰는 저장 경로다.
        UUID id = UUID.randomUUID();
        Creator creator = creatorWithDisplayFields(
                id, "insert경로채널" + UUID.randomUUID(),
                "https://i.ytimg.com/vi/fixtureVid1/hqdefault.jpg", "채널 소개", "@masiton-fixture");

        // when
        Creator inserted = creatorRepositoryPort.insertIfAbsent(creator).orElseThrow();

        // then
        assertThat(inserted.getProfileImageUrl()).isEqualTo("https://i.ytimg.com/vi/fixtureVid1/hqdefault.jpg");
        assertThat(inserted.getDescription()).isEqualTo("채널 소개");
        assertThat(inserted.getHandle()).isEqualTo("@masiton-fixture");
    }

    @Test
    @DisplayName("표시 정보 세 필드가 없으면 저장 후 findById로 null 그대로 조회된다")
    void 저장_표시정보없음_findById가null을그대로조회한다() {
        // given
        UUID id = UUID.randomUUID();
        Creator creator = creatorWithDisplayFields(
                id, "표시정보없음채널" + UUID.randomUUID(), null, null, null);

        // when: 등록 흐름과 같은 insertIfAbsent 경로로 저장한다. save()는 신규 Entity의 감사 컬럼을
        // JPA Auditing 기본 DateTimeProvider(LocalDateTime)로 채우려 해 OffsetDateTime 변환이
        // 실패하며, 운영 코드가 쓰지 않는 경로다.
        creatorRepositoryPort.insertIfAbsent(creator);
        Creator found = creatorRepositoryPort.findById(id).orElseThrow();

        // then
        assertThat(found.getProfileImageUrl()).isNull();
        assertThat(found.getDescription()).isNull();
        assertThat(found.getHandle()).isNull();
    }

    private Creator creatorWithDisplayFields(
            UUID id, String channelName, String profileImageUrl, String description, String handle) {
        return new Creator(
                id,
                "UC-" + UUID.randomUUID(),
                channelName,
                "https://example.com/channel/" + id,
                profileImageUrl,
                description,
                handle,
                PublicationStatus.PUBLIC,
                LifecycleStatus.ACTIVE,
                ExternalAvailabilityStatus.AVAILABLE,
                OffsetDateTime.now(),
                null,
                null,
                null);
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
