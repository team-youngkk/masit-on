package com.masiton.visit.infrastructure.persistence;

import java.util.UUID;

/**
 * VisitQueryJpaRepository native query 결과를 매핑하는 Spring Data 인터페이스 기반 Projection이다.
 * Infrastructure 내부 전용이며 VisitQueryPersistenceAdapter가 application.port.out.VisitContentRow로 변환한다.
 */
interface VisitContentProjection {

    UUID getCreatorId();

    String getChannelName();

    String getChannelUrl();

    UUID getVideoId();

    String getTitle();

    String getThumbnailUrl();

    String getSourceUrl();
}
