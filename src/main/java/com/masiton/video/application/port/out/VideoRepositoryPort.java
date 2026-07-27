package com.masiton.video.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.masiton.video.domain.model.Video;

/**
 * video 도메인이 저장소에 요구하는 출력 Port다.
 * Infrastructure Adapter가 구현하며, Application은 이 인터페이스에만 의존한다.
 */
public interface VideoRepositoryPort {

    Video save(Video video);

    Optional<Video> findById(UUID id);
}
