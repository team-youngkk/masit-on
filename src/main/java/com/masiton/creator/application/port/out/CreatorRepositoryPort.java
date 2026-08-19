package com.masiton.creator.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.masiton.creator.domain.model.Creator;

public interface CreatorRepositoryPort {

    Creator save(Creator creator);

    Optional<Creator> findById(UUID id);

    Optional<Creator> findByExternalChannelId(String externalChannelId);

    Optional<Creator> insertIfAbsent(Creator creator);
    /**
     * 공개(PUBLIC)·활성(ACTIVE) Creator를 channel_name 오름차순(동률 시 id 오름차순)으로
     * 이미 정렬해 반환한다. 애플리케이션은 반환 순서를 다시 정렬하지 않는다.
     */
    List<Creator> findPublicSelectionList();

}
