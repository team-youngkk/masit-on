package com.masiton.ai.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * E3-T08의 합성·비식별 AI 추출 골든 데이터 프로그램 평가기다.
 * 실제 운영 {@link AiCandidateValidator}에 S1 payload를 실행하되 외부 제공자 호출과 LLM 심판은 하지 않는다.
 */
@DisplayName("기존 Preview 후보의 AI 영상 정보 추출 골든 데이터 평가(EVAL-AI-001~010)")
class AiExtractionGoldenEvaluationTest {

    private static final String DATASET_ROOT = "/eval/aiextract-golden-v1.0.0/";
    private static final String RELEASE_HOLDOUT_PROPERTY = "masiton.eval.releaseHoldout";
    private static final String RELEASE_HOLDOUT = "RELEASE_HOLDOUT";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final AiCandidateValidator VALIDATOR = new AiCandidateValidator();

    @Test
    @DisplayName("데이터셋 규모와 분할, 평가 ID는 manifest 계약과 일치한다")
    void 데이터셋은_120건이고_분할과유일키를_지킨다() {
        JsonNode manifest = readJson("manifest.json");
        List<EvaluationCase> cases = loadCases();
        Map<String, Long> actualSplits = countBy(cases, EvaluationCase::split);
        Set<String> actualEvalIds = cases.stream()
                .flatMap(testCase -> testCase.evalIds().stream())
                .collect(Collectors.toSet());

        assertThat(cases).hasSize(manifest.get("caseCount").asInt());
        manifest.get("splits").properties().forEach(entry ->
                assertThat(actualSplits).containsEntry(entry.getKey(), entry.getValue().asLong()));
        assertThat(actualSplits).hasSize(manifest.get("splits").size());
        assertThat(actualEvalIds).containsExactlyInAnyOrderElementsOf(textList(manifest.get("evalIds")));
        assertThat(cases.stream().map(EvaluationCase::groupId).distinct().count())
                .isEqualTo(manifest.get("semanticGroupCount").asLong());
        assertThat(cases.stream().map(EvaluationCase::payloadVariant).distinct().count())
                .isEqualTo(manifest.get("distinctPayloadVariants").asLong());
        assertUnique(cases, EvaluationCase::caseId, "caseId");
        assertUnique(cases, EvaluationCase::fixtureRef, "fixtureRef");
        assertThat(cases).allSatisfy(testCase -> {
            assertThat(testCase.fixtureRef()).matches("[0-9a-f]{64}");
            assertThat(testCase.sourceClassification()).isEqualTo("TEAM_AUTHORED_SYNTHETIC");
        });
        assertThat(manifest.get("fixtureReference").get("contentHashClaimed").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("같은 그룹과 맛집 정답은 서로 다른 분할에 누수되지 않는다")
    void 같은그룹과맛집정답은_분할간누수되지않는다() {
        List<EvaluationCase> cases = loadCases();

        assertSingleSplitPerValue(cases, EvaluationCase::groupId, "groupId");
        assertSingleSplitPerValue(cases, EvaluationCase::groundTruthRestaurantName, "restaurantName");
    }

    @Test
    @DisplayName("동명 장소와 허위 주소 등 위험 경계와 EVAL-AI-001부터 010까지를 추적한다")
    void 위험경계와_모든EVAL_ID를_추적한다() {
        List<EvaluationCase> cases = loadCases();
        Set<String> scenarios = cases.stream().map(EvaluationCase::scenario).collect(Collectors.toSet());

        assertThat(scenarios).contains(
                "SAME_NAME_MULTIPLE_PLACE_CANDIDATES",
                "SYNTHETIC_FALSE_ADDRESS_WITHHELD",
                "INDIRECT_RESTAURANT_MENTION",
                "EXPLICIT_NOT_VISITED",
                "MULTIPLE_RESTAURANTS_IN_ONE_INPUT",
                "SCHEMA_DEVIATION_UNEXPECTED_ROOT_FIELD",
                "AMBIGUOUS_ADDRESS_WITHOUT_EVIDENCE");
        for (int number = 1; number <= 10; number++) {
            String evalId = "EVAL-AI-%03d".formatted(number);
            assertThat(cases).anySatisfy(testCase -> assertThat(testCase.evalIds()).contains(evalId));
        }
        assertThat(cases).allSatisfy(testCase -> assertThat(testCase.evalIds())
                .allMatch(evalId -> evalId.matches("EVAL-AI-00[1-9]|EVAL-AI-010")));
    }

    @Test
    @DisplayName("원본 URL와 영상, 자막, 응답 및 Prompt 전문, 개인정보와 비밀을 보존하지 않는다")
    void 평가자산은_금지된원문과민감정보를_보존하지않는다() {
        JsonNode manifest = readJson("manifest.json");
        JsonNode cases = readJson("cases.json");
        JsonNode provenance = manifest.get("provenance");

        assertThat(provenance.get("sourceClassification").asText()).isEqualTo("TEAM_AUTHORED_SYNTHETIC");
        for (String field : List.of("containsOriginalUrls", "containsOriginalVideo", "containsTranscriptOrCaption",
                "containsProviderResponseBody", "containsPromptBody", "containsPersonalData", "containsSecrets")) {
            assertThat(provenance.get(field).asBoolean()).as(field).isFalse();
        }
        assertThat(cases.toString()).doesNotContain("http://", "https://", "youtube.com", "youtu.be",
                "apiKey", "accessToken", "refreshToken", "transcriptText", "promptBody", "providerResponseBody");
        for (JsonNode testCase : cases.get("cases")) {
            JsonNode input = testCase.get("inputFixture");
            assertThat(input.get("originalUrlStored").asBoolean()).isFalse();
            assertThat(input.get("originalVideoStored").asBoolean()).isFalse();
            assertThat(input.get("transcriptStored").asBoolean()).isFalse();
        }
    }

    @Test
    @DisplayName("인간 사후 판정 대기와 E3-T13 전 운영 비활성은 출시 결정을 HOLD로 만든다")
    void 인간판정대기와_운영게이트대기는_출시결정을HOLD로_만든다() {
        JsonNode manifest = readJson("manifest.json");
        List<EvaluationCase> cases = loadCases();
        JsonNode policy = manifest.get("evaluationPolicy");
        JsonNode runtime = manifest.get("runtimeContract");

        assertThat(runtime.get("provider").asText()).isEqualTo(AiExtractionContract.PROVIDER);
        assertThat(runtime.get("modelVersion").asText()).isEqualTo("gemini-3-flash-preview");
        // 골든 자산은 gemini-3-flash-preview·P1·S1 기준의 역사적 fixture다. 운영 계약이 올라가도 재판정 없이 보존한다.
        assertThat(runtime.get("promptVersion").asText()).isEqualTo("P1");
        assertThat(runtime.get("schemaVersion").asText()).isEqualTo("S1");
        assertThat(cases).allSatisfy(testCase -> {
            assertThat(testCase.humanStatus()).isEqualTo("PENDING");
            assertThat(testCase.judgeRole()).isNotBlank();
            assertThat(testCase.humanReason()).isNotBlank();
            assertThat(testCase.judgedAt()).isNull();
            assertThat(testCase.disagreementStatus()).isEqualTo("UNRESOLVED");
        });
        assertThat(manifest.get("humanAdjudication").get("completedHumanApprovalClaimed").asBoolean()).isFalse();
        assertThat(policy.get("llmJudgeEnabled").asBoolean()).isFalse();
        assertThat(policy.get("productionProviderCallsAllowed").asBoolean()).isFalse();
        assertThat(policy.get("productionActivationAllowed").asBoolean()).isFalse();
        assertThat(policy.get("activationGateTask").asText()).isEqualTo("E3-T13");
        assertThat(policy.get("releaseDecision").asText()).isEqualTo("HOLD");
    }

    @Test
    @DisplayName("기존 Preview 골든 평가와 운영 모델이 다르면 활성화를 보류한다")
    void 기존Preview골든평가와운영모델이_다르면_활성화를보류한다() {
        JsonNode manifest = readJson("manifest.json");
        JsonNode policy = manifest.get("evaluationPolicy");
        JsonNode runtime = manifest.get("runtimeContract");

        assertThat(runtime.get("modelVersion").asText()).isEqualTo("gemini-3-flash-preview");
        assertThat(runtime.get("modelVersion").asText()).isNotEqualTo(AiExtractionContract.MODEL_VERSION);
        assertThat(AiExtractionContract.MODEL_VERSION).isEqualTo("gemini-3.5-flash-lite");
        assertThat(policy.get("productionActivationAllowed").asBoolean()).isFalse();
        assertThat(policy.get("releaseDecision").asText()).isEqualTo("HOLD");
    }

    @Test
    @DisplayName("롤백 준비는 신규 호출 중지와 fallback, 기존 Entity 보존, 미종결 작업 처리를 포함한다")
    void 롤백준비는_네가지안전조건을_포함한다() {
        JsonNode rollback = readJson("manifest.json").get("rollbackReadiness");

        assertThat(rollback.get("stopNewCalls").asBoolean()).isTrue();
        assertThat(rollback.get("usePreviousApprovedVersionOrNonAiFallback").asBoolean()).isTrue();
        assertThat(rollback.get("deleteExistingEntitiesAutomatically").asBoolean()).isFalse();
        assertThat(rollback.get("unfinishedJobsHandledExplicitly").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("합성 S1 dry-run은 expected와 일치하지만 Release 품질 증거로 사용하지 않는다")
    void 합성S1_dryRun은_expected와일치하지만_Release품질증거가아니다() {
        List<EvaluationCase> evaluated = loadCases().stream()
                .filter(testCase -> releaseHoldoutEnabled() || !RELEASE_HOLDOUT.equals(testCase.split()))
                .toList();
        List<String> violations = new ArrayList<>();
        for (EvaluationCase testCase : evaluated) {
            AiCandidateValidationResult result = VALIDATOR.validate(buildPayload(testCase));
            if (!testCase.expectedDecision().equals(result.decision().name())) {
                violations.add(testCase.caseId() + ": decision expected=" + testCase.expectedDecision()
                        + ", actual=" + result.decision());
            }
            for (String issueCode : testCase.requiredIssueCodes()) {
                if (!result.reasonCodes().contains(issueCode)) {
                    violations.add(testCase.caseId() + ": required issue 누락=" + issueCode);
                }
            }
            if (!Set.copyOf(testCase.expectedTags()).equals(tagCodes(result.tags()))) {
                violations.add(testCase.caseId() + ": connectable tag 불일치");
            }
            if (!Set.copyOf(testCase.expectedRejectedTags()).equals(tagCodes(result.rejectedTags()))) {
                violations.add(testCase.caseId() + ": rejected tag 불일치");
            }
            if (!Objects.equals(testCase.expectedRestaurantName(), candidateValue(result, "restaurantName"))) {
                violations.add(testCase.caseId() + ": expected restaurantName 불일치");
            }
            if (!Objects.equals(testCase.expectedAddress(), candidateValue(result, "address"))) {
                violations.add(testCase.caseId() + ": expected address 불일치");
            }
            boolean visitEvidencePresent = result.candidates().containsKey("visitEvidence")
                    && result.candidates().get("visitEvidence").size() == 1
                    && result.candidates().get("visitEvidence").get(0).evidence().type()
                    != AiCandidateValidationResult.EvidenceType.UNKNOWN;
            if (testCase.expectedVisitEvidencePresent() != visitEvidencePresent) {
                violations.add(testCase.caseId() + ": expected visitEvidencePresent 불일치");
            }
            if (!Set.copyOf(testCase.expectedTags()).equals(Set.copyOf(testCase.allowedTags()))
                    || testCase.automaticRegistrationExpected()
                    != "AUTO_CONFIRMED".equals(testCase.expectedDecision())) {
                violations.add(testCase.caseId() + ": expected-groundTruth 계약 불일치");
            }
            if ((testCase.expectedRestaurantName() != null
                    && !Objects.equals(testCase.expectedRestaurantName(), testCase.groundTruthRestaurantName()))
                    || (testCase.expectedAddress() != null
                    && !Objects.equals(testCase.expectedAddress(), testCase.groundTruthAddress()))) {
                violations.add(testCase.caseId() + ": validator 후보와 groundTruth 값 불일치");
            }
            boolean incompleteGroundTruth = testCase.groundTruthRestaurantName() == null
                    || testCase.groundTruthAddress() == null
                    || !testCase.groundTruthVisited();
            if (incompleteGroundTruth && testCase.automaticRegistrationExpected()) {
                violations.add(testCase.caseId() + ": 불완전 groundTruth가 자동 등록 기대값을 가짐");
            }
            if (testCase.criticalWrongPlace() && result.isAutoConfirmable()) {
                violations.add(testCase.caseId() + ": Critical 위험 사례가 자동 확정됨");
            }
            if ("ADDRESS_UNKNOWN".equals(testCase.payloadVariant())) {
                boolean hasAddressUnknown = result.issues().stream()
                        .anyMatch(issue -> "UNKNOWN_EVIDENCE".equals(issue.code())
                                && "address".equals(issue.field()));
                boolean hasLocationUnknown = result.issues().stream()
                        .anyMatch(issue -> "UNKNOWN_EVIDENCE".equals(issue.code())
                                && "location".equals(issue.field()));
                if (!hasAddressUnknown || hasLocationUnknown) {
                    violations.add(testCase.caseId() + ": UNKNOWN_EVIDENCE field 불일치");
                }
            }
        }

        assertThat(evaluated).hasSize(releaseHoldoutEnabled() ? 120 : 96);
        assertThat(violations).isEmpty();
        JsonNode policy = readJson("manifest.json").get("evaluationPolicy");
        assertThat(policy.get("syntheticValidatorDryRunIsReleaseEvidence").asBoolean()).isFalse();
        assertThat(policy.get("qualityMetricsStatus").asText()).isEqualTo("UNMEASURED");
        assertThat(policy.get("criticalMislinkStatus").asText()).isEqualTo("UNADJUDICATED");
        assertThat(policy.get("releaseDecision").asText()).isEqualTo("HOLD");
        JsonNode manifest = readJson("manifest.json");
        long criticalCases = loadCases().stream().filter(EvaluationCase::criticalWrongPlace).count();
        assertThat(criticalCases)
                .isEqualTo(manifest.get("criticalRiskGroundTruth").get("positiveCaseCount").asLong());
        assertThat(manifest.get("criticalRiskGroundTruth")
                .get("syntheticDetectionIsReleaseQualityEvidence").asBoolean()).isFalse();
        assertThat(textList(manifest.get("linkedRegressionEvidence").get("EVAL-AI-006")))
                .containsExactly("TST-E3-AI-003", "TST-E3-DATA-001");
        assertThat(textList(manifest.get("linkedRegressionEvidence").get("EVAL-AI-008")))
                .containsExactly("TST-E3-AI-004");
    }

    private static JsonNode buildPayload(EvaluationCase testCase) {
        int number = testCase.fixtureOrdinal();
        String name = "합성맛집-%03d".formatted(number);
        String address = "서울특별시 합성구 평가로 " + number;
        String validCandidates = """
                {"field":"restaurantName","value":"%s","confidence":0.96,"evidence":{"type":"TIMESTAMP","startMs":100,"endMs":180}},
                {"field":"address","value":"%s","confidence":0.94,"evidence":{"type":"TIMESTAMP","startMs":181,"endMs":260}},
                {"field":"location","value":"합성구","confidence":0.92,"evidence":{"type":"TIMESTAMP","startMs":181,"endMs":260}},
                {"field":"visitEvidence","value":"직접 방문","confidence":0.95,"evidence":{"type":"TIMESTAMP","startMs":261,"endMs":340}}
                """.formatted(name, address);

        String json = switch (testCase.payloadVariant()) {
            case "AUTO_CONFIRMED_WITH_TAGS" -> payload("COMPLETE", validCandidates + "," + validTags(number), "");
            case "AUTO_CONFIRMED_NO_TAG" -> payload("COMPLETE", validCandidates, "");
            case "ADDRESS_UNKNOWN" -> payload("COMPLETE", validCandidates.replaceFirst(Pattern.quote(
                    "{\"type\":\"TIMESTAMP\",\"startMs\":181,\"endMs\":260}"),
                    "{\"type\":\"UNKNOWN\"}"), "");
            case "MULTIPLE_ADDRESS_CANDIDATES" -> payload("COMPLETE", validCandidates + ","
                    + candidate("address", "서울특별시 다른구 후보로 " + number, 350, 420), "");
            case "MISSING_ADDRESS" -> payload("PARTIAL", validCandidates.replaceFirst(
                    "(?s)\\s*\\{\\\"field\\\":\\\"address\\\".*?\\},", ""), "\"address\"");
            case "VISIT_UNKNOWN" -> payload("COMPLETE", validCandidates.replace(
                    "{\"type\":\"TIMESTAMP\",\"startMs\":261,\"endMs\":340}",
                    "{\"type\":\"UNKNOWN\"}"), "");
            case "MULTIPLE_RESTAURANT_CANDIDATES" -> payload("COMPLETE", validCandidates + ","
                    + candidate("restaurantName", "다른 합성맛집-" + number, 350, 420), "");
            case "INVALID_ROOT_FIELD" -> payload("COMPLETE", validCandidates, "")
                    .replaceFirst("}$", ",\"unexpectedField\":true}");
            case "AUTO_CONFIRMED_UNKNOWN_TAG" -> payload("COMPLETE", validCandidates + ","
                    + unknownTag(number), "");
            case "INVALID_CONFIDENCE" -> payload("COMPLETE", validCandidates.replaceFirst(
                    "\\\"confidence\\\":0.96", "\\\"confidence\\\":1.20"), "");
            case "MISSING_RESTAURANT" -> payload("PARTIAL", validCandidates.replaceFirst(
                    "(?s)\\{\\\"field\\\":\\\"restaurantName\\\".*?\\},", ""), "\"restaurantName\"");
            default -> throw new IllegalArgumentException("알 수 없는 payloadVariant: " + testCase.payloadVariant());
        };
        return OBJECT_MAPPER.readTree(json);
    }

    private static String payload(String completeness, String candidates, String missingFields) {
        return "{\"resultCompleteness\":\"%s\",\"candidates\":[%s],\"missingFields\":[%s]}"
                .formatted(completeness, candidates, missingFields);
    }

    private static String candidate(String field, String value, int startMs, int endMs) {
        return ("{\"field\":\"%s\",\"value\":\"%s\",\"confidence\":0.91,"
                + "\"evidence\":{\"type\":\"TIMESTAMP\",\"startMs\":%d,\"endMs\":%d}}"
                ).formatted(field, value, startMs, endMs);
    }

    private static String validTags(int number) {
        return tag(number, "MENU", "합성메뉴", "MENU_SYNTHETIC", 400) + ","
                + tag(number, "OCCASION", "혼밥", "OCCASION_SOLO", 500);
    }

    private static String tag(int number, String type, String label, String code, int startMs) {
        return ("{\"field\":\"tag\",\"candidateTagId\":\"tag-%03d-%s\",\"tagType\":\"%s\","
                + "\"rawLabel\":\"%s\",\"normalizedCode\":\"%s\",\"label\":\"%s\","
                + "\"confidence\":0.93,\"evidence\":{\"type\":\"TIMESTAMP\",\"startMs\":%d,\"endMs\":%d}}"
                ).formatted(number, type, type, label, code, label, startMs, startMs + 60);
    }

    private static String unknownTag(int number) {
        return ("{\"field\":\"tag\",\"candidateTagId\":\"tag-%03d-unknown\",\"tagType\":\"TASTE\","
                + "\"rawLabel\":\"불명\",\"normalizedCode\":\"TASTE_UNKNOWN\",\"label\":\"불명\","
                + "\"confidence\":0.60,\"evidence\":{\"type\":\"UNKNOWN\"}}").formatted(number);
    }

    private static Set<String> tagCodes(List<AiCandidateValidationResult.TagCandidate> tags) {
        return tags.stream().map(AiCandidateValidationResult.TagCandidate::normalizedCode)
                .collect(Collectors.toSet());
    }

    private static String candidateValue(AiCandidateValidationResult result, String field) {
        List<AiCandidateValidationResult.Candidate> candidates = result.candidates().get(field);
        return candidates == null || candidates.size() != 1 ? null : candidates.get(0).value();
    }

    private static boolean releaseHoldoutEnabled() {
        return Boolean.getBoolean(RELEASE_HOLDOUT_PROPERTY);
    }

    private static List<EvaluationCase> loadCases() {
        List<EvaluationCase> cases = new ArrayList<>();
        for (JsonNode node : readJson("cases.json").get("cases")) {
            JsonNode input = node.get("inputFixture");
            JsonNode expected = node.get("expected");
            JsonNode truth = node.get("groundTruth");
            JsonNode human = node.get("humanReview");
            cases.add(new EvaluationCase(
                    node.get("caseId").asText(), node.get("groupId").asText(), node.get("split").asText(),
                    textList(node.get("evalIds")), node.get("scenario").asText(), node.get("payloadVariant").asText(),
                    node.get("fixtureOrdinal").asInt(), input.get("fixtureRef").asText(),
                    input.get("sourceClassification").asText(), expected.get("validatorDecision").asText(),
                    nullableText(expected.get("restaurantName")), nullableText(expected.get("address")),
                    expected.get("visitEvidencePresent").asBoolean(),
                    textList(expected.get("connectableTagCodes")), textList(expected.get("rejectedTagCodes")),
                    textList(expected.get("requiredIssueCodes")), nullableText(truth.get("restaurantName")),
                    nullableText(truth.get("address")), truth.get("visited").asBoolean(),
                    textList(truth.get("allowedTagCodes")), truth.get("automaticRegistrationExpected").asBoolean(),
                    truth.get("criticalWrongPlace").asBoolean(),
                    human.get("status").asText(), human.get("judgeRole").asText(), human.get("reason").asText(),
                    nullableText(human.get("judgedAt")), human.get("disagreementStatus").asText()));
        }
        return List.copyOf(cases);
    }

    private static JsonNode readJson(String name) {
        String path = DATASET_ROOT + name;
        try (InputStream input = AiExtractionGoldenEvaluationTest.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("평가 자산을 찾을 수 없습니다: " + path);
            }
            return OBJECT_MAPPER.readTree(input);
        } catch (IOException exception) {
            throw new IllegalStateException("평가 자산을 읽을 수 없습니다: " + path, exception);
        }
    }

    private static List<String> textList(JsonNode node) {
        List<String> values = new ArrayList<>();
        for (JsonNode element : node) {
            values.add(element.asText());
        }
        return List.copyOf(values);
    }

    private static String nullableText(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static Map<String, Long> countBy(
            List<EvaluationCase> cases, Function<EvaluationCase, String> classifier) {
        return cases.stream().collect(Collectors.groupingBy(classifier, Collectors.counting()));
    }

    private static void assertUnique(
            List<EvaluationCase> cases, Function<EvaluationCase, String> classifier, String label) {
        assertThat(cases.stream().map(classifier).distinct().count()).as(label).isEqualTo(cases.size());
    }

    private static void assertSingleSplitPerValue(
            List<EvaluationCase> cases, Function<EvaluationCase, String> classifier, String label) {
        Map<String, Set<String>> splitsByValue = cases.stream()
                .filter(testCase -> classifier.apply(testCase) != null)
                .collect(Collectors.groupingBy(classifier,
                        Collectors.mapping(EvaluationCase::split, Collectors.toCollection(HashSet::new))));
        assertThat(splitsByValue).allSatisfy((value, splits) ->
                assertThat(splits).as(label + "=" + value).hasSize(1));
    }

    private record EvaluationCase(
            String caseId,
            String groupId,
            String split,
            List<String> evalIds,
            String scenario,
            String payloadVariant,
            int fixtureOrdinal,
            String fixtureRef,
            String sourceClassification,
            String expectedDecision,
            String expectedRestaurantName,
            String expectedAddress,
            boolean expectedVisitEvidencePresent,
            List<String> expectedTags,
            List<String> expectedRejectedTags,
            List<String> requiredIssueCodes,
            String groundTruthRestaurantName,
            String groundTruthAddress,
            boolean groundTruthVisited,
            List<String> allowedTags,
            boolean automaticRegistrationExpected,
            boolean criticalWrongPlace,
            String humanStatus,
            String judgeRole,
            String humanReason,
            String judgedAt,
            String disagreementStatus) {
    }

}
