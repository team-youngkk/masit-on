package com.masiton.restaurant.application.naturallanguage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * EVAL-NL-001~004·007의 프로그램 평가 자산이다. Release holdout은 변경 튜닝에
 * 사용하지 않고 {@code -Dmasiton.eval.releaseHoldout=true}일 때만 품질 지표에 포함한다.
 * EVAL-NL-005는 PostgreSQL 통합 테스트, EVAL-NL-006은 공개 화면·브라우저 인수가 맡는다.
 */
@DisplayName("자연어 맛집 탐색 P1 골든 데이터셋 평가(EVAL-NL-001~004·007)")
class NaturalLanguageEvaluationGoldenV1Test {

    private static final String CASES_PATH = "/eval/nlsearch-golden-v1.0.0/cases.json";
    private static final String MANIFEST_PATH = "/eval/nlsearch-golden-v1.0.0/manifest.json";
    private static final String RELEASE_HOLDOUT_PROPERTY = "masiton.eval.releaseHoldout";
    private static final String RELEASE_HOLDOUT = "RELEASE_HOLDOUT";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> EXPECTED_DISTRICTS = Set.of(
            "종로구", "중구", "용산구", "성동구", "광진구", "동대문구", "중랑구", "성북구", "강북구", "도봉구",
            "노원구", "은평구", "서대문구", "마포구", "양천구", "강서구", "구로구", "금천구", "영등포구",
            "동작구", "관악구", "서초구", "강남구", "송파구", "강동구");
    private static final Set<String> EXPECTED_CATEGORIES = Set.of(
            "한식", "중식", "일식", "양식", "동남아 음식", "인도·남아시아 음식", "분식", "카페·디저트", "술집·주점", "기타");
    private static final Set<String> EXPECTED_TAGS = Set.of(
            "MENU_NAENGMYEON", "MENU_GUKBAP", "MENU_RAMEN", "MENU_SUSHI", "MENU_PIZZA", "MENU_SAMGYEOPSAL",
            "TASTE_SPICY", "TASTE_SWEET", "TASTE_SAVORY", "TASTE_LIGHT", "OCCASION_SOLO", "OCCASION_DATE",
            "OCCASION_GROUP", "OCCASION_LATE_NIGHT", "ATMOSPHERE_CASUAL", "ATMOSPHERE_QUIET",
            "ATMOSPHERE_LIVELY", "ATMOSPHERE_BAR");

    @Test
    @DisplayName("데이터셋은 40개 의미 그룹·240개 문장과 144/48/48 분할을 고정한다")
    void 데이터셋_구조와_분할을_검증한다() {
        JsonNode manifest = read(MANIFEST_PATH);
        List<EvaluationCase> cases = loadCases();

        assertThat(manifest.get("datasetId").asText()).isEqualTo("nlsearch-golden-v1.0.0");
        assertThat(manifest.get("parserVersion").asText()).isEqualTo("P1");
        assertThat(manifest.get("caseCount").asInt()).isEqualTo(240);
        assertThat(cases).hasSize(240);
        assertThat(cases.stream().map(EvaluationCase::groupId).distinct()).hasSize(40);
        assertThat(cases.stream().collect(Collectors.groupingBy(EvaluationCase::split, Collectors.counting())))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "DEVELOPMENT", 144L,
                        "CALIBRATION", 48L,
                        RELEASE_HOLDOUT, 48L));
    }

    @Test
    @DisplayName("문장·case ID는 중복되지 않고 문장 변형 그룹은 하나의 분할에만 속한다")
    void 중복과_분할간_그룹누수를_차단한다() {
        List<EvaluationCase> cases = loadCases();

        assertThat(cases.stream().map(EvaluationCase::caseId).distinct()).hasSize(240);
        assertThat(cases.stream().map(EvaluationCase::sentence).distinct()).hasSize(240);
        Map<String, Set<String>> groupSplits = cases.stream().collect(Collectors.groupingBy(
                EvaluationCase::groupId,
                Collectors.mapping(EvaluationCase::split, Collectors.toSet())));
        assertThat(groupSplits.values()).allSatisfy(splits -> assertThat(splits).hasSize(1));
        assertThat(cases.stream().collect(Collectors.groupingBy(EvaluationCase::groupId, Collectors.counting()))
                .values()).allSatisfy(count -> assertThat(count).isEqualTo(6L));
    }

    @Test
    @DisplayName("P1 사전의 25개 자치구·10개 카테고리·18개 태그와 opaque Creator를 포함한다")
    void 운영_P1_사전_범위를_빠짐없이_포함한다() {
        List<EvaluationCase> cases = loadCases();

        assertThat(expectedScalarValues(cases, "district")).containsAll(EXPECTED_DISTRICTS);
        assertThat(expectedScalarValues(cases, "category")).containsAll(EXPECTED_CATEGORIES);
        assertThat(expectedTags(cases)).containsExactlyInAnyOrderElementsOf(EXPECTED_TAGS);
        assertThat(expectedScalarValues(cases, "creatorId")).contains("creator-opaque-eval");
        assertThat(cases.stream().flatMap(testCase -> testCase.evalIds().stream()).collect(Collectors.toSet()))
                .contains("EVAL-NL-001", "EVAL-NL-002", "EVAL-NL-003", "EVAL-NL-004", "EVAL-NL-007");
    }

    @Test
    @DisplayName("조건 exact match 90%·지원 조건 재현율 95%·미지원 오적용 0건을 충족한다")
    void EVAL_NL_001부터_003_품질목표를_충족한다() {
        EvaluationStats stats = evaluate(enabledCases());

        assertThat(stats.exactMatchRate())
                .as("EVAL-NL-001 exact condition set: %s/%s", stats.exactMatches(), stats.exactCases())
                .isGreaterThanOrEqualTo(0.90);
        assertThat(stats.supportedRecall())
                .as("EVAL-NL-002 supported atomic conditions: %s/%s", stats.recoveredAtoms(), stats.expectedAtoms())
                .isGreaterThanOrEqualTo(0.95);
        assertThat(stats.unsupportedFalseApplications())
                .as("EVAL-NL-003 unsupported expressions applied as supported filters")
                .isZero();
    }

    @Test
    @DisplayName("직접 필터 우선·상태·미지원 이유·태그 AND 기대값 위반이 0건이다")
    void EVAL_NL_004와_007_사례별_계약을_충족한다() {
        List<String> violations = new ArrayList<>();
        NaturalLanguageRestaurantParser parser = parser();

        for (EvaluationCase testCase : enabledCases()) {
            NaturalLanguageParseResult actual = parser.parse(testCase.sentence(), testCase.directFilters());
            if (actual.status() != testCase.expectedStatus()) {
                violations.add(testCase.caseId() + " status expected=" + testCase.expectedStatus()
                        + " actual=" + actual.status());
            }
            Set<String> actualReasons = actual.interpretation().ignoredConditions().stream()
                    .map(IgnoredCondition::reason)
                    .collect(Collectors.toSet());
            if (!actualReasons.equals(testCase.expectedIgnoredReasons())) {
                violations.add(testCase.caseId() + " ignoredReasons expected=" + testCase.expectedIgnoredReasons()
                        + " actual=" + actualReasons);
            }
            if (testCase.evalIds().contains("EVAL-NL-003")) {
                Set<String> actualIgnoredTexts = actual.interpretation().ignoredConditions().stream()
                        .filter(condition -> testCase.expectedIgnoredReasons().contains(condition.reason()))
                        .map(IgnoredCondition::text)
                        .collect(Collectors.toSet());
                if (!actualIgnoredTexts.containsAll(testCase.expectedIgnoredTexts())) {
                    violations.add(testCase.caseId() + " ignoredTexts expected=" + testCase.expectedIgnoredTexts()
                            + " actual=" + actualIgnoredTexts);
                }
            }
            Set<String> actualConflictFields = actual.interpretation().conflicts().stream()
                    .map(conflict -> conflict.field().name())
                    .collect(Collectors.toSet());
            if (!actualConflictFields.equals(testCase.expectedConflictFields())) {
                violations.add(testCase.caseId() + " conflicts expected=" + testCase.expectedConflictFields()
                        + " actual=" + actualConflictFields);
            }
            if (testCase.evalIds().contains("EVAL-NL-007")
                    && !new HashSet<>(actual.appliedConditions().tags())
                            .equals(new HashSet<>(testCase.expectedFilters().tags()))) {
                violations.add(testCase.caseId() + " tag AND set mismatch");
            }
        }

        assertThat(violations).isEmpty();
    }

    private static EvaluationStats evaluate(List<EvaluationCase> cases) {
        NaturalLanguageRestaurantParser parser = parser();
        int exactMatches = 0;
        int exactCases = 0;
        int recoveredAtoms = 0;
        int expectedAtoms = 0;
        int unsupportedFalseApplications = 0;

        for (EvaluationCase testCase : cases) {
            NaturalLanguageParseResult parseResult = parser.parse(testCase.sentence(), testCase.directFilters());
            NaturalLanguageFilters actual = parseResult.appliedConditions();
            NaturalLanguageFilters expected = testCase.expectedFilters();
            if (testCase.evalIds().contains("EVAL-NL-001")) {
                exactCases++;
                if (sameConditions(actual, expected)) {
                    exactMatches++;
                }
            }
            if (testCase.evalIds().contains("EVAL-NL-002")) {
                Set<String> expectedConditionAtoms = atoms(expected);
                expectedAtoms += expectedConditionAtoms.size();
                Set<String> actualConditionAtoms = atoms(actual);
                actualConditionAtoms.retainAll(expectedConditionAtoms);
                recoveredAtoms += actualConditionAtoms.size();
            }
            if (testCase.evalIds().contains("EVAL-NL-003")) {
                Set<String> unexpected = atoms(actual);
                unexpected.removeAll(atoms(expected));
                unsupportedFalseApplications += unexpected.size();
            }
        }
        return new EvaluationStats(
                exactMatches,
                exactCases,
                recoveredAtoms,
                expectedAtoms,
                unsupportedFalseApplications);
    }

    private static boolean sameConditions(NaturalLanguageFilters actual, NaturalLanguageFilters expected) {
        return java.util.Objects.equals(actual.query(), expected.query())
                && java.util.Objects.equals(actual.district(), expected.district())
                && java.util.Objects.equals(actual.category(), expected.category())
                && java.util.Objects.equals(actual.creatorId(), expected.creatorId())
                && new HashSet<>(actual.tags()).equals(new HashSet<>(expected.tags()));
    }

    private static Set<String> atoms(NaturalLanguageFilters filters) {
        Set<String> atoms = new LinkedHashSet<>();
        addAtom(atoms, "query", filters.query());
        addAtom(atoms, "district", filters.district());
        addAtom(atoms, "category", filters.category());
        addAtom(atoms, "creatorId", filters.creatorId());
        filters.tags().forEach(tag -> addAtom(atoms, "tag", tag));
        return atoms;
    }

    private static void addAtom(Set<String> atoms, String field, String value) {
        if (value != null) {
            atoms.add(field + "=" + value);
        }
    }

    private static NaturalLanguageRestaurantParser parser() {
        return new NaturalLanguageRestaurantParser(NaturalLanguageDictionary.standard(
                Map.of("creator-opaque-eval", "먹방연구소")));
    }

    private static List<EvaluationCase> enabledCases() {
        return loadCases().stream()
                .filter(testCase -> Boolean.getBoolean(RELEASE_HOLDOUT_PROPERTY)
                        || !RELEASE_HOLDOUT.equals(testCase.split()))
                .toList();
    }

    private static List<EvaluationCase> loadCases() {
        JsonNode root = read(CASES_PATH);
        List<EvaluationCase> cases = new ArrayList<>();
        for (JsonNode group : root.get("groups")) {
            String groupId = group.get("groupId").asText();
            String split = group.get("split").asText();
            String boundary = group.get("boundary").asText();
            List<String> evalIds = strings(group.get("evalIds"));
            NaturalLanguageFilters directFilters = filters(group.get("directFilters"));
            JsonNode expected = group.get("expected");
            NaturalLanguageFilters expectedFilters = filters(expected.get("applied"));
            InterpretationStatus expectedStatus = InterpretationStatus.valueOf(expected.get("status").asText());
            Set<String> ignoredReasons = Set.copyOf(strings(expected.get("ignoredReasons")));
            Set<String> conflictFields = Set.copyOf(strings(expected.get("conflictFields")));
            List<String> sentences = strings(group.get("sentences"));
            List<String> ignoredTextBySentence = strings(group.get("ignoredTextBySentence"));
            if (!ignoredTextBySentence.isEmpty() && ignoredTextBySentence.size() != sentences.size()) {
                throw new IllegalStateException(groupId + " ignoredTextBySentence 수가 sentences와 다릅니다");
            }
            for (int index = 0; index < sentences.size(); index++) {
                cases.add(new EvaluationCase(
                        groupId + "-" + (index + 1),
                        groupId,
                        split,
                        boundary,
                        evalIds,
                        sentences.get(index),
                        directFilters,
                        expectedStatus,
                        expectedFilters,
                        ignoredReasons,
                        ignoredTextBySentence.isEmpty() ? Set.of() : Set.of(ignoredTextBySentence.get(index)),
                        conflictFields));
            }
        }
        return List.copyOf(cases);
    }

    private static Set<String> expectedScalarValues(List<EvaluationCase> cases, String field) {
        return cases.stream()
                .map(EvaluationCase::expectedFilters)
                .map(filters -> switch (field) {
                    case "district" -> filters.district();
                    case "category" -> filters.category();
                    case "creatorId" -> filters.creatorId();
                    default -> throw new IllegalArgumentException("Unknown field: " + field);
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static Set<String> expectedTags(List<EvaluationCase> cases) {
        return cases.stream()
                .flatMap(testCase -> testCase.expectedFilters().tags().stream())
                .collect(Collectors.toSet());
    }

    private static NaturalLanguageFilters filters(JsonNode node) {
        if (node == null || node.isNull()) {
            return NaturalLanguageFilters.empty();
        }
        return new NaturalLanguageFilters(
                nullableText(node.get("query")),
                nullableText(node.get("district")),
                nullableText(node.get("category")),
                nullableText(node.get("creatorId")),
                strings(node.get("tags")));
    }

    private static List<String> strings(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(value -> values.add(value.asText()));
        return List.copyOf(values);
    }

    private static String nullableText(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static JsonNode read(String path) {
        try (InputStream input = NaturalLanguageEvaluationGoldenV1Test.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("평가 파일을 찾을 수 없습니다: " + path);
            }
            return MAPPER.readTree(input);
        } catch (IOException e) {
            throw new IllegalStateException("평가 파일을 읽을 수 없습니다: " + path, e);
        }
    }

    private record EvaluationCase(
            String caseId,
            String groupId,
            String split,
            String boundary,
            List<String> evalIds,
            String sentence,
            NaturalLanguageFilters directFilters,
            InterpretationStatus expectedStatus,
            NaturalLanguageFilters expectedFilters,
            Set<String> expectedIgnoredReasons,
            Set<String> expectedIgnoredTexts,
            Set<String> expectedConflictFields) {
    }

    private record EvaluationStats(
            int exactMatches,
            int exactCases,
            int recoveredAtoms,
            int expectedAtoms,
            int unsupportedFalseApplications) {

        double exactMatchRate() {
            return exactCases == 0 ? 0 : (double) exactMatches / exactCases;
        }

        double supportedRecall() {
            return expectedAtoms == 0 ? 0 : (double) recoveredAtoms / expectedAtoms;
        }
    }
}
