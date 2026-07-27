package com.masiton.video.domain.model;

/**
 * video 테이블의 외부 제공자(YouTube) 가용성 상태다.
 * DB CHECK 제약({@code ck_video__external_availability_status})의 허용값과 이름이 같아야 한다.
 */
public enum ExternalAvailabilityStatus {

    AVAILABLE,
    UNAVAILABLE
}
