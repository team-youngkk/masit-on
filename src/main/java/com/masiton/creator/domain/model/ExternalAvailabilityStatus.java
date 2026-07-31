package com.masiton.creator.domain.model;

/**
 * creator 테이블의 외부 채널 가용 상태다.
 * DB CHECK 제약({@code ck_creator__external_availability_status})의 허용값과 이름이 같아야 한다.
 */
public enum ExternalAvailabilityStatus {

    AVAILABLE,
    UNAVAILABLE
}
