package com.masiton.visit.domain.model;

/**
 * visit의 생명주기 상태다.
 * DB CHECK 제약({@code ck_visit__lifecycle_status})의 허용값과 이름이 같아야 한다.
 */
public enum LifecycleStatus {

    ACTIVE,
    DELETED
}
