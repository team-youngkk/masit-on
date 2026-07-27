package com.masiton.security.domain.model;

/**
 * confirmation_token 테이블의 resource_type 컬럼과 대응하는 확인 대상 자원 종류다.
 * DB CHECK 제약({@code ck_confirmation_token__resource_type})의 허용값과 이름이 같아야 한다.
 */
public enum ConfirmationTokenResourceType {

    RESTAURANT,
    CREATOR,
    VIDEO
}
