package com.masiton.creator.application.port.in;

import java.util.List;
import java.util.UUID;

/** 관리자 YouTube 채널 감시 화면에 필요한 Creator 참조 목록이다. */
public interface FindYoutubeChannelWatchCreatorsUseCase {

    List<CreatorReference> findAll();

    record CreatorReference(UUID id, String channelName, String externalChannelId,
                            boolean publiclyVisible, boolean externallyAvailable) { }
}
