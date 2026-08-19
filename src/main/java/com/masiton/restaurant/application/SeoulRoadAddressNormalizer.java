package com.masiton.restaurant.application;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Kakao 축약 시도명과 관리자 주소 힌트를 도메인의 서울특별시 표기로 맞춘다. */
public final class SeoulRoadAddressNormalizer {

    private static final Pattern SEOUL_ROAD_ADDRESS_DISTRICT = Pattern.compile("^서울특별시\\s+([^\\s]+구)\\s+.+$");

    private SeoulRoadAddressNormalizer() {
    }

    public static String normalize(String roadAddress) {
        String normalized = roadAddress.strip();
        if (normalized.startsWith("서울특별시")) {
            return normalized;
        }
        if (normalized.startsWith("서울 ")) {
            return "서울특별시 " + normalized.substring("서울 ".length()).strip();
        }
        return normalized;
    }

    /**
     * 정규화된("서울특별시 OO구 ...") 도로명주소에서 자치구를 뽑는다. 서울 주소가 아니거나
     * 자치구를 뽑을 수 없으면 빈 값을 반환한다. 호출자가 원문을 먼저 {@link #normalize}해야 한다.
     */
    public static Optional<String> extractDistrict(String normalizedRoadAddress) {
        if (normalizedRoadAddress == null) {
            return Optional.empty();
        }
        Matcher matcher = SEOUL_ROAD_ADDRESS_DISTRICT.matcher(normalizedRoadAddress.trim());
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
