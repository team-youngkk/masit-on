package com.masiton.creator.application.query;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.creator.application.port.in.CreatorSelectionItem;
import com.masiton.creator.application.port.in.GetPublicCreatorSelectionListUseCase;
import com.masiton.creator.application.port.out.CreatorRepositoryPort;

@Service
@Transactional(readOnly = true)
public class CreatorSelectionListQueryService implements GetPublicCreatorSelectionListUseCase {

    private final CreatorRepositoryPort creatorRepositoryPort;

    public CreatorSelectionListQueryService(CreatorRepositoryPort creatorRepositoryPort) {
        this.creatorRepositoryPort = creatorRepositoryPort;
    }

    @Override
    public List<CreatorSelectionItem> getPublicSelectionList() {
        return creatorRepositoryPort.findPublicSelectionList().stream()
                .map(creator -> new CreatorSelectionItem(
                        creator.getId(), creator.getChannelName(), creator.getProfileImageUrl()))
                .toList();
    }
}
