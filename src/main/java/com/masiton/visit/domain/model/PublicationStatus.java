package com.masiton.visit.domain.model;

/**
 * visit의 공개 상태다.
 * DB CHECK 제약({@code ck_visit__publication_status})의 허용값과 이름이 같아야 한다.
 */
public enum PublicationStatus {

    PUBLIC,
    PRIVATE
}
