package com.masiton.ai.application.port.out;

/**
 * 같은 {@code ai_registration_unit} 행에 대한 동시 요청이 행 잠금을 얻지 못했을 때 던진다.
 * 호출자는 {@code 409 AIEXTRACT_CONCURRENT_REQUEST_CONFLICT}로 변환한다. 업무 중복을 뜻하는
 * {@code blockReason}의 {@code DUPLICATE_CONFLICT}와는 다른 개념이다.
 */
public class AiRegistrationUnitConcurrentAccessException extends RuntimeException {

    public AiRegistrationUnitConcurrentAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
