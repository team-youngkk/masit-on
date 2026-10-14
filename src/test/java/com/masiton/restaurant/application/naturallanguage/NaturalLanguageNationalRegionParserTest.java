package com.masiton.restaurant.application.naturallanguage;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("P2 활성 전국 지역 해석")
class NaturalLanguageNationalRegionParserTest {

    private final NaturalLanguageRestaurantParser parser = new NaturalLanguageRestaurantParser(
            NaturalLanguageDictionary.national(Map.of("creator-opaque", "먹방연구소"),
                    Map.of("MENU_GUKBAP", List.of("국밥"))),
            new NaturalLanguageRegionResolver(NationalRegionTestFixture.regions()));

    @Test
    @DisplayName("부모 문맥은 동명 자식의 코드를 구분하고 부모 코드와 충돌하지 않는다")
    void parse_부모와동명자식_해당하위코드를반환한다() {
        // given & when & then
        assertThat(parser.parse("서울 중구 한식").appliedConditions().regionCode()).isEqualTo("1114000000");
        assertThat(parser.parse("부산 중구 한식").appliedConditions().regionCode()).isEqualTo("2611000000");
        assertThat(parser.parse("경기도 수원시 한식").appliedConditions().regionCode()).isEqualTo("4111000000");
        assertThat(parser.parse("부산 해운대구 한식").status()).isEqualTo(InterpretationStatus.APPLIED);
    }

    @Test
    @DisplayName("부모 없는 동명·불일치·미등록·비활성 지역은 임의 확정하지 않는다")
    void parse_미해결지역_코드와이름조건을반환하지않는다() {
        // given & when & then
        for (String sentence : List.of("중구", "강서구", "부산 마포구", "서울 부산", "없는구", "휴면구", "가림시", "인천 남구")) {
            var result = parser.parse(sentence);
            assertThat(result.status()).as(sentence).isEqualTo(InterpretationStatus.FAILED);
            assertThat(result.appliedConditions().regionCode()).as(sentence).isNull();
            assertThat(result.appliedConditions().query()).as(sentence).isNull();
            assertThat(result.interpretation().unresolvedFields()).as(sentence).contains(ConditionField.REGION_CODE);
        }
    }

    @Test
    @DisplayName("미해결 지역과 유효 음식 조건은 PARTIAL 및 명시적 미해결로 반환한다")
    void parse_미해결지역과음식_PARTIAL로반환한다() {
        // given & when
        var result = parser.parse("중구 한식");
        // then
        assertThat(result.status()).isEqualTo(InterpretationStatus.PARTIAL);
        assertThat(result.appliedConditions().category()).isEqualTo("한식");
        assertThat(result.interpretation().ignoredConditions()).extracting(IgnoredCondition::reason)
                .contains("UNRESOLVED_VALUE");
    }

    @Test
    @DisplayName("활성 서울 동네 별칭·세종·제주와 기존 AND 조건을 유지한다")
    void parse_활성지역별칭과AND_정확한코드와조건을반환한다() {
        // given & when & then
        assertThat(parser.parse("성수에서 한식집").appliedConditions().regionCode()).isEqualTo("1120000000");
        assertThat(parser.parse("홍대 맛집").appliedConditions().regionCode()).isEqualTo("1144000000");
        assertThat(parser.parse("세종 맛집").appliedConditions().regionCode()).isEqualTo("3611000000");
        assertThat(parser.parse("제주 맛집").appliedConditions().regionCode()).isEqualTo("5000000000");
        assertThat(parser.parse("제주 서귀포시").appliedConditions().regionCode()).isEqualTo("5013000000");
        var combined = parser.parse("부산 해운대구 먹방연구소가 방문한 한식 국밥 '가게 이름'");
        assertThat(combined.status()).isEqualTo(InterpretationStatus.APPLIED);
        assertThat(combined.appliedConditions()).isEqualTo(new NaturalLanguageFilters(
                "가게 이름", null, "한식", "creator-opaque", List.of("MENU_GUKBAP"), "2635000000"));
        assertThat(combined.interpretation().parserVersion()).isEqualTo("P2");
        assertThat(parser.parse("부산 해운대구 국밥 태그").status()).isEqualTo(InterpretationStatus.APPLIED);
        var overlapParser = new NaturalLanguageRestaurantParser(
                NaturalLanguageDictionary.national(Map.of(), Map.of("TAG_BUSAN", List.of("부산"))),
                new NaturalLanguageRegionResolver(NationalRegionTestFixture.regions()));
        assertThat(overlapParser.parse("부산 중구").appliedConditions().regionCode()).isEqualTo("2611000000");
        assertThat(overlapParser.parse("부산 중구").appliedConditions().tags()).containsExactly("TAG_BUSAN");
    }

    @Test
    @DisplayName("지명 문자열의 내부·접두사를 별도 지역으로 적용하지 않는다")
    void parse_다른단어내지명_지역코드를적용하지않는다() {
        // given & when & then
        for (String sentence : List.of("강남김밥", "서울식당", "가짜부산", "마포구이")) {
            assertThat(parser.parse(sentence).appliedConditions().regionCode()).as(sentence).isNull();
        }
        assertThat(parser.parse("'독도' 한식").appliedConditions().regionCode()).isNull();
        assertThat(parser.parse("'대구' 한식").status()).isEqualTo(InterpretationStatus.APPLIED);
        assertThat(parser.parse("한식에서 맛집").status()).isEqualTo(InterpretationStatus.APPLIED);
        assertThat(parser.parse("혹시 한식 맛집").status()).isEqualTo(InterpretationStatus.APPLIED);
        assertThat(parser.parse("다시 부산 한식 맛집").status()).isEqualTo(InterpretationStatus.APPLIED);
        assertThat(parser.parse("부산구").status()).isEqualTo(InterpretationStatus.FAILED);
        assertThat(parser.parse("광주 한식").status()).isEqualTo(InterpretationStatus.PARTIAL);
        assertThat(parser.parse("전남 한식").interpretation().unresolvedFields()).contains(ConditionField.REGION_CODE);
    }
}
