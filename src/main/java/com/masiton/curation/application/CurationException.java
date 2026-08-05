package com.masiton.curation.application;

import org.springframework.http.HttpStatus;

import com.masiton.common.web.BusinessException;

public final class CurationException extends BusinessException {
    private CurationException(HttpStatus status, String code, String message) { super(status, code, message); }
    public static CurationException notFound() { return new CurationException(HttpStatus.NOT_FOUND, "CURATION_NOT_FOUND", "큐레이션을 찾을 수 없습니다."); }
    public static CurationException restaurantNotFound() { return new CurationException(HttpStatus.NOT_FOUND, "RESTAURANT_NOT_FOUND", "요청한 맛집을 찾을 수 없습니다."); }
    public static CurationException duplicateRestaurant() { return new CurationException(HttpStatus.CONFLICT, "DUPLICATE_CURATION_RESTAURANT", "맛집을 중복해서 구성할 수 없습니다."); }
    public static CurationException restaurantLimit() { return new CurationException(HttpStatus.CONFLICT, "CURATION_RESTAURANT_LIMIT_EXCEEDED", "큐레이션에는 맛집을 최대 20개까지 구성할 수 있습니다."); }
    public static CurationException publicationLimit() { return new CurationException(HttpStatus.CONFLICT, "PUBLISHED_CURATION_LIMIT_EXCEEDED", "게시 큐레이션은 최대 5개입니다."); }
    public static CurationException invalidMainOrder() { return new CurationException(HttpStatus.CONFLICT, "INVALID_MAIN_CURATION_ORDER", "현재 게시 중인 큐레이션 전체의 순서를 지정해 주세요."); }
}
