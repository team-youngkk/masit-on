package com.masiton.restaurant.application.port.out;

/** Monthly permit for Kakao Mobility calls. */
@FunctionalInterface
public interface CourseRouteQuotaPort {

    boolean tryAcquireMonthlyPermit();

    default boolean tryAcquireRequestPermit() {
        return true;
    }

    default void releaseRequestPermit() {
    }
}
