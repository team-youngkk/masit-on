package com.masiton.ai.application.port.out;

/** YouTube backfill API 호출 비용을 원자적으로 예약하는 외부 Port. */
@FunctionalInterface
public interface YoutubeChannelBackfillQuotaPort {

    boolean tryReserve(int cost);
}
