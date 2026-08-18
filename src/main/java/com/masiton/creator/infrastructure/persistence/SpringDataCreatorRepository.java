package com.masiton.creator.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;


interface SpringDataCreatorRepository extends JpaRepository<CreatorJpaEntity, UUID> {

    Optional<CreatorJpaEntity> findByExternalChannelId(String externalChannelId);

    /**
     * ix_creator__public_name 부분 인덱스((channel_name COLLATE "C", id)
     * WHERE publication_status='PUBLIC' AND lifecycle_status='ACTIVE'
     * AND external_availability_status='AVAILABLE')를 그대로 쓰도록 WHERE·ORDER BY를
     * 인덱스 정의와 같은 표현으로 맞춘다. ck_creator__external_unavailable_private 제약이
     * PUBLIC이면 항상 AVAILABLE임을 보장하므로 결과 집합은 PUBLIC·ACTIVE만 거른 것과 같다.
     */
    @Query(
            value = "SELECT * FROM creator "
                    + "WHERE publication_status = 'PUBLIC' "
                    + "AND lifecycle_status = 'ACTIVE' "
                    + "AND external_availability_status = 'AVAILABLE' "
                    + "ORDER BY channel_name COLLATE \"C\", id",
            nativeQuery = true)
    List<CreatorJpaEntity> findPublicSelectionList();

}
