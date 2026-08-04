package com.masiton.participation.application;

import org.springframework.http.HttpStatus;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorResponse;

public class ParticipationException extends BusinessException {

    public ParticipationException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static ParticipationException duplicateSubmission(ErrorResponse.ResourceReference resource) {
        return new ParticipationException(
                HttpStatus.CONFLICT,
                "DUPLICATE_OPEN_SUBMISSION",
                "이미 처리 중인 같은 제보가 있습니다.",
                resource);
    }

    public static ParticipationException duplicateReport(ErrorResponse.ResourceReference resource) {
        return new ParticipationException(
                HttpStatus.CONFLICT,
                "DUPLICATE_OPEN_REPORT",
                "이미 처리 중인 같은 신고가 있습니다.",
                resource);
    }

    private ParticipationException(
            HttpStatus status,
            String code,
            String message,
            ErrorResponse.ResourceReference resource
    ) {
        super(status, code, message, resource);
    }
}
