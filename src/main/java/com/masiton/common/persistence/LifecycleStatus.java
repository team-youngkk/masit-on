package com.masiton.common.persistence;

/**
 * restaurant, creator, video, visit 네 테이블이 공유하는 생명주기 상태다.
 * DB CHECK 제약({@code ck_{table}__lifecycle_status})의 허용값과 이름이 같아야 한다.
 */
public enum LifecycleStatus {

    ACTIVE,
    DELETED
}
