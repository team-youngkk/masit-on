package com.masiton.restaurant.application.naturallanguage;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.masiton.restaurant.domain.model.Region;

final class NationalRegionTestFixture {

    private NationalRegionTestFixture() {
    }

    static List<Region> regions() {
        return List.of(
                region("1100000000", "서울특별시", null, true),
                region("1200000000", "전남광주통합특별시", null, true),
                region("1211000000", "목포시", "1200000000", true),
                region("1224000000", "서구", "1200000000", true),
                region("2600000000", "부산광역시", null, true),
                region("4100000000", "경기도", null, true),
                region("3611000000", "세종특별자치시", null, true),
                region("5000000000", "제주특별자치도", null, true),
                region("1144000000", "마포구", "1100000000", true),
                region("1120000000", "성동구", "1100000000", true),
                region("1168000000", "강남구", "1100000000", true),
                region("1114000000", "중구", "1100000000", true),
                region("2611000000", "중구", "2600000000", true),
                region("1150000000", "강서구", "1100000000", true),
                region("2644000000", "강서구", "2600000000", true),
                region("2635000000", "해운대구", "2600000000", true),
                region("4111000000", "수원시", "4100000000", true),
                region("5011000000", "제주시", "5000000000", true),
                region("5013000000", "서귀포시", "5000000000", true),
                region("1199000000", "휴면구", "1100000000", false),
                region("9900000000", "휴면도", null, false),
                region("9911000000", "가림시", "9900000000", true));
    }

    static Region region(String code, String name, String parentCode, boolean active) {
        OffsetDateTime time = OffsetDateTime.parse("2026-10-11T00:00:00Z");
        return new Region(id(code), "REGION_" + code, name, (short) 0, active, time, time, code,
                parentCode == null ? null : id(parentCode));
    }

    private static UUID id(String code) {
        return UUID.nameUUIDFromBytes(code.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
