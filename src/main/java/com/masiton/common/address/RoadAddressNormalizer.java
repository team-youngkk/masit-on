package com.masiton.common.address;

import java.util.Map;
import java.util.Optional;

/** 시도 별칭을 공식 명칭으로 맞추고 주소의 시도·시군구 경계를 해석한다. */
public final class RoadAddressNormalizer {

    private static final Map<String, String> PROVINCES = Map.ofEntries(
            Map.entry("서울", "서울특별시"), Map.entry("서울시", "서울특별시"),
            Map.entry("부산", "부산광역시"), Map.entry("부산시", "부산광역시"),
            Map.entry("대구", "대구광역시"), Map.entry("대구시", "대구광역시"),
            Map.entry("인천", "인천광역시"), Map.entry("인천시", "인천광역시"),
            Map.entry("광주", "전남광주통합특별시"), Map.entry("광주시", "전남광주통합특별시"),
            Map.entry("광주광역시", "전남광주통합특별시"),
            Map.entry("전남광주", "전남광주통합특별시"),
            Map.entry("전라남도", "전남광주통합특별시"),
            Map.entry("대전", "대전광역시"), Map.entry("대전시", "대전광역시"),
            Map.entry("울산", "울산광역시"), Map.entry("울산시", "울산광역시"),
            Map.entry("세종", "세종특별자치시"), Map.entry("세종시", "세종특별자치시"),
            Map.entry("경기", "경기도"),
            Map.entry("강원", "강원특별자치도"), Map.entry("강원도", "강원특별자치도"),
            Map.entry("충북", "충청북도"), Map.entry("충남", "충청남도"),
            Map.entry("전북", "전북특별자치도"), Map.entry("전라북도", "전북특별자치도"),
            Map.entry("전남", "전남광주통합특별시"), Map.entry("경북", "경상북도"), Map.entry("경남", "경상남도"),
            Map.entry("제주", "제주특별자치도"), Map.entry("제주도", "제주특별자치도"));

    private RoadAddressNormalizer() {
    }

    public static String normalize(String address) {
        if (address == null) {
            return null;
        }
        String[] tokens = address.strip().split("(?U)\\s+", 2);
        String province = PROVINCES.getOrDefault(tokens[0], tokens[0]);
        return tokens.length == 1 ? province : province + " " + tokens[1];
    }

    public static Optional<RegionAddress> extractRegion(String address) {
        String normalized = normalize(address);
        if (normalized == null || normalized.isBlank()) {
            return Optional.empty();
        }
        String[] tokens = normalized.split("(?U)\\s+");
        if (!PROVINCES.containsValue(tokens[0]) || tokens.length < 3) {
            return Optional.empty();
        }
        if ("세종특별자치시".equals(tokens[0])) {
            // 세종은 별도의 시군구 없이 시도 자체에 배정한다.
            return tokens[1].matches(".+[시군구]") ? Optional.empty()
                    : Optional.of(new RegionAddress(tokens[0], tokens[0]));
        }
        if (!tokens[1].matches(".+[시군구]")) {
            return Optional.empty();
        }
        // 수원시 영통구 같은 비자치구는 시에 귀속하되, 구 이름까지만 있는 주소는 거부한다.
        if (tokens[1].endsWith("시") && tokens[2].endsWith("구") && tokens.length < 4) {
            return Optional.empty();
        }
        return Optional.of(new RegionAddress(tokens[0], tokens[1]));
    }

    public record RegionAddress(String province, String municipality) {
    }
}
