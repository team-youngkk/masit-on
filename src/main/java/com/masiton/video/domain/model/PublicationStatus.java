package com.masiton.video.domain.model;

/**
 * video의 공개 상태다.
 * DB CHECK 제약({@code ck_video__publication_status})의 허용값과 이름이 같아야 한다.
 */
public enum PublicationStatus {

    PUBLIC,
    PRIVATE
}
