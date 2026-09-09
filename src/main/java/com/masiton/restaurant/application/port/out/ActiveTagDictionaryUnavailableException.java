package com.masiton.restaurant.application.port.out;

/** 활성 태그 사전 snapshot을 정본 저장소에서 읽지 못했을 때 발생한다. */
public final class ActiveTagDictionaryUnavailableException extends RuntimeException {

    public ActiveTagDictionaryUnavailableException(Throwable cause) {
        super("ACTIVE_TAG_DICTIONARY_UNAVAILABLE", cause);
    }
}
