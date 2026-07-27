package com.masiton.security.domain.model;

/**
 * confirmation_token 테이블의 status 컬럼과 대응하는 확인 토큰 상태다.
 * DB CHECK 제약({@code ck_confirmation_token__status}, {@code ck_confirmation_token__completion_pair})의
 * 허용값과 이름이 같아야 한다.
 */
public enum ConfirmationTokenStatus {

    ISSUED,
    CREATED,
    DUPLICATE
}
