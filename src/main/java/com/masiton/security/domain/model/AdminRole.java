package com.masiton.security.domain.model;

/**
 * admin_account 테이블의 role 컬럼과 대응하는 관리자 역할이다.
 * DB CHECK 제약({@code ck_admin_account__role})이 'ADMIN' 하나만 허용하므로 MVP는
 * 단일 상수만 둔다. 문자열 상수 대신 enum으로 둔 이유는 restaurant·visit 도메인의
 * PublicationStatus·LifecycleStatus와 마찬가지로 고정 어휘 컬럼을 도메인 전 계층에서
 * 동일한 타입으로 다루기 위해서이며, 값이 하나뿐이라는 이유로 문자열로 낮추면 오탈자 방지와
 * {@code @Enumerated(EnumType.STRING)} 매핑 일관성을 잃는다.
 */
public enum AdminRole {

    ADMIN
}
