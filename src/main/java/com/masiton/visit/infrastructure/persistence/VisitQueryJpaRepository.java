package com.masiton.visit.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Infrastructure 내부 전용 조회 Repository다. visit·restaurant·creator·video는 같은 데이터베이스의
 * 물리 테이블이므로 native SQL 안에서 테이블명으로 JOIN한다. 다른 도메인의 JPA Entity나
 * Spring Data Repository를 Java 코드에서 import하지 않는다(ArchitectureTest
 * "도메인간_persistence_직접의존을_금지한다" 규칙).
 *
 * <p>판정 조건(BR-VISIT-005): Visit·Restaurant는 PUBLIC/ACTIVE, Creator·Video는
 * PUBLIC/ACTIVE와 외부 AVAILABLE을 모두 만족해야 한다. index-strategy.md 2·4절의
 * {@code ix_visit__creator_restaurant}, {@code ix_visit__restaurant_creator} partial index를
 * 그대로 활용하도록 조건 순서를 인덱스 컬럼 순서와 맞춘다.
 */
interface VisitQueryJpaRepository extends Repository<VisitJpaEntity, UUID> {

    @Query(
            value = "SELECT DISTINCT v.restaurant_id "
                    + "FROM visit v "
                    + "JOIN restaurant r ON r.id = v.restaurant_id "
                    + "JOIN creator c ON c.id = v.creator_id "
                    + "JOIN video vi ON vi.id = v.video_id "
                    + "WHERE v.creator_id = :creatorId "
                    + "AND v.publication_status = 'PUBLIC' AND v.lifecycle_status = 'ACTIVE' "
                    + "AND r.publication_status = 'PUBLIC' AND r.lifecycle_status = 'ACTIVE' "
                    + "AND c.publication_status = 'PUBLIC' AND c.lifecycle_status = 'ACTIVE' "
                    + "AND c.external_availability_status = 'AVAILABLE' "
                    + "AND vi.publication_status = 'PUBLIC' AND vi.lifecycle_status = 'ACTIVE' "
                    + "AND vi.external_availability_status = 'AVAILABLE'",
            nativeQuery = true)
    List<UUID> findDistinctValidRestaurantIdsByCreatorId(@Param("creatorId") UUID creatorId);

    @Query(
            value = "SELECT c.id AS creatorId, c.channel_name AS channelName, c.channel_url AS channelUrl, "
                    + "vi.id AS videoId, vi.title AS title, vi.thumbnail_url AS thumbnailUrl, "
                    + "vi.source_url AS sourceUrl "
                    + "FROM visit v "
                    + "JOIN restaurant r ON r.id = v.restaurant_id "
                    + "JOIN creator c ON c.id = v.creator_id "
                    + "JOIN video vi ON vi.id = v.video_id "
                    + "WHERE v.restaurant_id = :restaurantId "
                    + "AND v.publication_status = 'PUBLIC' AND v.lifecycle_status = 'ACTIVE' "
                    + "AND r.publication_status = 'PUBLIC' AND r.lifecycle_status = 'ACTIVE' "
                    + "AND c.publication_status = 'PUBLIC' AND c.lifecycle_status = 'ACTIVE' "
                    + "AND c.external_availability_status = 'AVAILABLE' "
                    + "AND vi.publication_status = 'PUBLIC' AND vi.lifecycle_status = 'ACTIVE' "
                    + "AND vi.external_availability_status = 'AVAILABLE'",
            nativeQuery = true)
    List<VisitContentProjection> findValidVisitContentRowsByRestaurantId(@Param("restaurantId") UUID restaurantId);
}
