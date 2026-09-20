package com.masiton.restaurant.application.port.out;

import java.net.URI;

/** Revalidation is deliberately separate from registration preview verification. */
public interface KakaoPlaceRevalidationPort {
    Result verify(String kakaoPlaceId, String restaurantName, URI expectedPlaceUrl);

    record Result(Kind kind, VerifiedPlace place) {
        public static Result found(VerifiedPlace place) { return new Result(Kind.FOUND, place); }
        public static Result of(Kind kind) { return new Result(kind, null); }
    }
    enum Kind {
        FOUND, NOT_FOUND, PLACE_ID_MISMATCH, URL_MISMATCH, IDENTITY_AMBIGUOUS,
        QUOTA_EXCEEDED, TIMEOUT, EXTERNAL_FAILURE
    }
}
