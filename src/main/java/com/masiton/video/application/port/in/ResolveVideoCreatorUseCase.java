package com.masiton.video.application.port.in;

import java.util.UUID;

/** 게시 채널이 검증된 Creator와 일치할 때, 아직 미연결인 Video를 Creator에 연결한다. */
public interface ResolveVideoCreatorUseCase {

    FindVideoReferenceUseCase.VideoReference resolveCreator(UUID videoId, UUID creatorId);
}
