package com.masiton.creator.domain.model;

/**
 * creator의 공개 상태다.
 * DB CHECK 제약({@code ck_creator__publication_status})의 허용값과 이름이 같아야 한다.
 */
public enum PublicationStatus {

    PUBLIC,
    PRIVATE
}
