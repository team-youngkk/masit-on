package com.masiton.creator.application;

import org.springframework.http.HttpStatus;

import com.masiton.common.web.BusinessException;

public final class CreatorReferenceExceptionFactory {

    private CreatorReferenceExceptionFactory() {
    }

    public static BusinessException notFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "CREATOR_NOT_FOUND", "요청한 유튜버를 찾을 수 없습니다.");
    }
}
