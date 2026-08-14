package com.masiton.restaurant.application.naturallanguage;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * P1 parser가 사용하는 명시적 alias 사전이다. Creator는 DB를 조회하지 않고 호출자가
 * 공개 선택 목록 등에서 제공한 opaque ID와 alias를 주입해야 한다.
 */
public final class NaturalLanguageDictionary {

    private final EnumMap<ConditionField, Map<String, Set<String>>> aliases;

    private NaturalLanguageDictionary(EnumMap<ConditionField, Map<String, Set<String>>> aliases) {
        this.aliases = new EnumMap<>(ConditionField.class);
        for (ConditionField field : ConditionField.values()) {
            Map<String, Set<String>> fieldAliases = aliases.getOrDefault(field, Map.of());
            LinkedHashMap<String, Set<String>> copy = new LinkedHashMap<>();
            fieldAliases.forEach((alias, values) -> copy.put(alias, Set.copyOf(values)));
            this.aliases.put(field, Collections.unmodifiableMap(copy));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static NaturalLanguageDictionary standard() {
        return standard(Map.of());
    }

    /** 공개 Creator 선택 목록을 P1의 opaque ID 별칭으로 추가한 사전을 만든다. */
    public static NaturalLanguageDictionary standard(Map<String, String> creatorAliases) {
        Builder builder = builder();

        addDistrict(builder, "종로구", "종로", "대학로");
        addDistrict(builder, "중구", "중구");
        addDistrict(builder, "용산구", "용산", "이태원");
        addDistrict(builder, "성동구", "성동", "성수", "성수동");
        addDistrict(builder, "광진구", "광진", "건대", "건대입구");
        addDistrict(builder, "동대문구", "동대문");
        addDistrict(builder, "중랑구", "중랑");
        addDistrict(builder, "성북구", "성북");
        addDistrict(builder, "강북구", "강북");
        addDistrict(builder, "도봉구", "도봉");
        addDistrict(builder, "노원구", "노원");
        addDistrict(builder, "은평구", "은평");
        addDistrict(builder, "서대문구", "서대문");
        addDistrict(builder, "마포구", "마포", "홍대", "합정");
        addDistrict(builder, "양천구", "양천");
        addDistrict(builder, "강서구", "강서");
        addDistrict(builder, "구로구", "구로");
        addDistrict(builder, "금천구", "금천");
        addDistrict(builder, "영등포구", "영등포", "여의도");
        addDistrict(builder, "동작구", "동작");
        addDistrict(builder, "관악구", "관악");
        addDistrict(builder, "서초구", "서초", "양재");
        addDistrict(builder, "강남구", "강남", "가로수길");
        addDistrict(builder, "송파구", "송파", "잠실");
        addDistrict(builder, "강동구", "강동");

        builder.category("한식", "한식", "한식집", "한식당")
                .category("중식", "중식", "중식집", "중국집")
                .category("일식", "일식", "일식집")
                .category("양식", "양식", "양식집", "이탈리안", "프렌치")
                .category("동남아 음식", "동남아", "동남아 음식", "태국 음식", "베트남 음식")
                .category("인도·남아시아 음식", "인도 음식", "인도·남아시아 음식", "커리")
                .category("분식", "분식", "분식집")
                .category("카페·디저트", "카페", "디저트", "카페·디저트")
                .category("술집·주점", "술집", "주점", "포차")
                .category("기타", "기타");

        builder.tag("MENU_NAENGMYEON", "냉면", "물냉면", "비빔냉면")
                .tag("MENU_GUKBAP", "국밥")
                .tag("MENU_RAMEN", "라멘")
                .tag("MENU_SUSHI", "스시", "초밥")
                .tag("MENU_PIZZA", "피자")
                .tag("MENU_SAMGYEOPSAL", "삼겹살")
                .tag("TASTE_SPICY", "매운", "매콤", "매운맛")
                .tag("TASTE_SWEET", "달콤", "달달", "단맛")
                .tag("TASTE_SAVORY", "고소", "감칠맛")
                .tag("TASTE_LIGHT", "담백", "깔끔한 맛")
                .tag("OCCASION_SOLO", "혼밥", "혼자 식사", "혼자 먹기")
                .tag("OCCASION_DATE", "데이트", "연인과")
                .tag("OCCASION_GROUP", "회식", "단체 모임", "모임")
                .tag("OCCASION_LATE_NIGHT", "야식", "늦은 밤", "심야")
                .tag("ATMOSPHERE_CASUAL", "캐주얼", "편안한 분위기")
                .tag("ATMOSPHERE_QUIET", "조용한", "조용한 분위기")
                .tag("ATMOSPHERE_LIVELY", "활기찬", "북적이는", "활기찬 분위기")
                .tag("ATMOSPHERE_BAR", "바 분위기", "포차 분위기");

        creatorAliases.forEach((creatorId, alias) -> {
            builder.creator(creatorId, alias);
        });

        return builder.build();
    }

    Map<String, Set<String>> aliasesFor(ConditionField field) {
        return aliases.getOrDefault(field, Map.of());
    }

    private static void addDistrict(Builder builder, String district, String... aliases) {
        builder.district(district, aliases);
    }

    private static String normalizeAlias(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public static final class Builder {

        private final EnumMap<ConditionField, Map<String, Set<String>>> aliases =
                new EnumMap<>(ConditionField.class);

        public Builder district(String district, String... aliases) {
            return add(ConditionField.DISTRICT, district, aliases);
        }

        public Builder category(String category, String... aliases) {
            return add(ConditionField.CATEGORY, category, aliases);
        }

        public Builder creator(String creatorId, String... aliases) {
            return add(ConditionField.CREATOR_ID, creatorId, aliases);
        }

        public Builder query(String restaurantName, String... aliases) {
            return add(ConditionField.QUERY, restaurantName, aliases);
        }

        public Builder tag(String tagCode, String... aliases) {
            return add(ConditionField.TAGS, tagCode, aliases);
        }

        public NaturalLanguageDictionary build() {
            return new NaturalLanguageDictionary(aliases);
        }

        private Builder add(ConditionField field, String value, String... rawAliases) {
            String normalizedValue = Objects.requireNonNull(value).trim();
            if (normalizedValue.isEmpty()) {
                throw new IllegalArgumentException("사전 값은 비어 있을 수 없습니다.");
            }
            Map<String, Set<String>> fieldAliases = aliases.computeIfAbsent(field, ignored -> new LinkedHashMap<>());
            List<String> allAliases = rawAliases == null || rawAliases.length == 0
                    ? List.of(normalizedValue)
                    : java.util.stream.Stream.concat(
                            java.util.stream.Stream.of(normalizedValue), java.util.Arrays.stream(rawAliases))
                            .toList();
            for (String rawAlias : allAliases) {
                String alias = normalizeAlias(Objects.requireNonNull(rawAlias));
                if (alias.isEmpty()) {
                    continue;
                }
                fieldAliases.computeIfAbsent(alias, ignored -> new LinkedHashSet<>()).add(normalizedValue);
            }
            return this;
        }
    }
}
