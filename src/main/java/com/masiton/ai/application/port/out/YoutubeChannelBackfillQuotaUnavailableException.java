package com.masiton.ai.application.port.out;

/** quota 저장소를 확인할 수 없어 YouTube 호출을 중단할 때 사용한다. */
public final class YoutubeChannelBackfillQuotaUnavailableException extends RuntimeException {

    public YoutubeChannelBackfillQuotaUnavailableException(Throwable cause) {
        super("YouTube backfill quota store is unavailable", cause);
    }
}
