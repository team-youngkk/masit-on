package com.masiton.restaurant.domain.model;

/**
 * restaurant의 공개 상태다.
 * DB CHECK 제약({@code ck_restaurant__publication_status})의 허용값과 이름이 같아야 한다.
 */
public enum PublicationStatus {

    PUBLIC,
    PRIVATE
}
