package com.masiton.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.ai.application.AiCandidateValidationResult.Decision;
import com.masiton.ai.application.AiCandidateValidationResult.Candidate;
import com.masiton.ai.application.AiCandidateValidationResult.Evidence;
import com.masiton.ai.application.AiCandidateValidationResult.ValidationIssue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@DisplayName("AI 후보 검증기")
class AiCandidateValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiCandidateValidator validator = new AiCandidateValidator();

    @Test
    @DisplayName("검증 결과의 후보 목록은 원본 변경과 외부 수정을 허용하지 않는다")
    void validationResult_후보목록_깊은불변성을보장한다() {
        // Given
        Candidate candidate = new Candidate(
                "restaurantName", "맛집", 0.95, Evidence.timestamp(100, 200));
        List<Candidate> sourceCandidates = new ArrayList<>(List.of(candidate));
        Map<String, List<Candidate>> source = new LinkedHashMap<>();
        source.put("restaurantName", sourceCandidates);

        // When
        AiCandidateValidationResult result = new AiCandidateValidationResult(
                Decision.AUTO_CONFIRMED, source, null, List.of(), List.of(), List.of(), List.of());
        sourceCandidates.clear();

        // Then
        assertThat(result.candidates().get("restaurantName")).containsExactly(candidate);
        assertThatThrownBy(() -> result.candidates().get("restaurantName").add(candidate))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("필수 후보와 메뉴가 단일 위치 근거를 가지면 자동 확정 가능으로 판정한다")
    void validate_필수후보와메뉴가유효하면_자동확정가능으로판정한다() throws Exception {
        // Given
        JsonNode payload = payload("""
                {
                  "resultCompleteness": "COMPLETE",
                  "candidates": [
                    {"field":"restaurantName","value":"맛집","confidence":0.95,"evidence":{"type":"TIMESTAMP","startMs":100,"endMs":200}},
                    {"field":"address","value":"서울시","confidence":0.90,"evidence":{"type":"TEXT_RANGE","startOffset":10,"endOffset":20,"sourceHash":"hash-1"}},
                    {"field":"location","value":"마포구","confidence":0.88,"evidence":{"type":"TIMESTAMP","startMs":100,"endMs":200}},
                    {"field":"visitEvidence","value":"직접 방문","confidence":0.91,"evidence":{"type":"TIMESTAMP","startMs":210,"endMs":300}},
                    {"field":"menu","value":"냉면","confidence":0.80,"evidence":{"type":"TIMESTAMP","startMs":300,"endMs":400}}
                  ],
                  "missingFields": []
                }
                """);

        // When
        AiCandidateValidationResult result = validator.validate(payload);

        // Then
        assertThat(result.decision()).isEqualTo(Decision.AUTO_CONFIRMED);
        assertThat(result.isAutoConfirmable()).isTrue();
        assertThat(result.candidates()).containsKeys("restaurantName", "address", "location", "visitEvidence");
        assertThat(result.foodCategoryName()).isEqualTo("냉면");
    }

    @Test
    @DisplayName("방문 근거가 텍스트 구간이면 자동 확정을 차단한다")
    void validate_방문근거가텍스트구간이면_자동확정을차단한다() throws Exception {
        // Given
        JsonNode payload = payload("""
                {
                  "resultCompleteness": "COMPLETE",
                  "candidates": [
                    {"field":"restaurantName","value":"맛집","confidence":0.95,"evidence":{"type":"TIMESTAMP","startMs":100,"endMs":200}},
                    {"field":"address","value":"서울시","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":100,"endMs":200}},
                    {"field":"location","value":"마포구","confidence":0.88,"evidence":{"type":"TIMESTAMP","startMs":100,"endMs":200}},
                    {"field":"visitEvidence","value":"직접 방문","confidence":0.91,"evidence":{"type":"TEXT_RANGE","startOffset":21,"endOffset":30,"sourceHash":"hash-1"}}
                  ],
                  "missingFields": []
                }
                """);

        // When
        AiCandidateValidationResult result = validator.validate(payload);

        // Then
        assertThat(result.decision()).isEqualTo(Decision.AUTO_BLOCKED);
        assertThat(result.isAutoConfirmable()).isFalse();
        assertThat(result.issues()).extracting(ValidationIssue::code)
                .contains("VISIT_EVIDENCE_REQUIRED");
    }

    @Test
    @DisplayName("PARTIAL 결과와 필수 후보 누락은 자동 확정 불가로 차단한다")
    void validate_PARTIAL과필수후보누락_자동확정불가로차단한다() throws Exception {
        // Given
        JsonNode payload = payload("""
                {
                  "resultCompleteness": "PARTIAL",
                  "candidates": [
                    {"field":"restaurantName","value":"맛집","confidence":0.95,"evidence":{"type":"TIMESTAMP","startMs":100,"endMs":200}},
                    {"field":"location","value":"마포구","confidence":0.88,"evidence":{"type":"TIMESTAMP","startMs":100,"endMs":200}},
                    {"field":"visitEvidence","value":"직접 방문","confidence":0.91,"evidence":{"type":"TIMESTAMP","startMs":100,"endMs":200}}
                  ],
                  "missingFields": ["address"]
                }
                """);

        // When
        AiCandidateValidationResult result = validator.validate(payload);

        // Then
        assertThat(result.decision()).isEqualTo(Decision.AUTO_BLOCKED);
        assertThat(result.missingFields()).contains("address");
        assertThat(result.reasonCodes()).contains("PARTIAL_RESULT", "MISSING_REQUIRED_FIELD");
    }

    @Test
    @DisplayName("필수 후보의 근거가 없거나 UNKNOWN이면 자동 확정하지 않는다")
    void validate_필수후보근거부족_자동확정하지않는다() throws Exception {
        // Given
        JsonNode payload = payload("""
                {
                  "resultCompleteness": "COMPLETE",
                  "candidates": [
                    {"field":"restaurantName","value":"맛집","confidence":0.95},
                    {"field":"address","value":"서울시","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":10,"endMs":20}},
                    {"field":"location","value":"마포구","confidence":0.88,"evidence":{"type":"UNKNOWN"}},
                    {"field":"visitEvidence","value":"직접 방문","confidence":0.91,"evidence":{"type":"TIMESTAMP","startMs":10,"endMs":20}}
                  ],
                  "missingFields": []
                }
                """);

        // When
        AiCandidateValidationResult result = validator.validate(payload);

        // Then
        assertThat(result.decision()).isEqualTo(Decision.AUTO_BLOCKED);
        assertThat(result.reasonCodes()).contains("MISSING_EVIDENCE", "UNKNOWN_EVIDENCE");
    }

    @Test
    @DisplayName("같은 필드에 복수 후보가 있으면 자동 확정하지 않는다")
    void validate_같은필드복수후보_자동확정하지않는다() throws Exception {
        // Given
        JsonNode payload = payload("""
                {
                  "resultCompleteness": "COMPLETE",
                  "candidates": [
                    {"field":"restaurantName","value":"첫 맛집","confidence":0.95,"evidence":{"type":"TIMESTAMP","startMs":10,"endMs":20}},
                    {"field":"restaurantName","value":"둘째 맛집","confidence":0.94,"evidence":{"type":"TIMESTAMP","startMs":30,"endMs":40}},
                    {"field":"address","value":"서울시","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":10,"endMs":20}},
                    {"field":"location","value":"마포구","confidence":0.88,"evidence":{"type":"TIMESTAMP","startMs":10,"endMs":20}},
                    {"field":"visitEvidence","value":"직접 방문","confidence":0.91,"evidence":{"type":"TIMESTAMP","startMs":10,"endMs":20}}
                  ],
                  "missingFields": []
                }
                """);

        // When
        AiCandidateValidationResult result = validator.validate(payload);

        // Then
        assertThat(result.decision()).isEqualTo(Decision.AUTO_BLOCKED);
        assertThat(result.reasonCodes()).contains("MULTIPLE_CANDIDATES");
        assertThat(result.candidates().get("restaurantName")).extracting(
                AiCandidateValidationResult.Candidate::value).containsExactly("첫 맛집", "둘째 맛집");
    }

    @Test
    @DisplayName("메뉴에 복수 후보가 있으면 자동 확정하지 않으면서도 후보를 모두 보존한다")
    void validate_메뉴복수후보_자동확정하지않으면서도후보를모두보존한다() throws Exception {
        // Given
        JsonNode payload = payload("""
                {
                  "resultCompleteness": "COMPLETE",
                  "candidates": [
                    {"field":"restaurantName","value":"맛집","confidence":0.95,"evidence":{"type":"TIMESTAMP","startMs":10,"endMs":20}},
                    {"field":"address","value":"서울시","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":10,"endMs":20}},
                    {"field":"location","value":"마포구","confidence":0.88,"evidence":{"type":"TIMESTAMP","startMs":10,"endMs":20}},
                    {"field":"visitEvidence","value":"직접 방문","confidence":0.91,"evidence":{"type":"TIMESTAMP","startMs":10,"endMs":20}},
                    {"field":"menu","value":"냉면","confidence":0.80,"evidence":{"type":"TIMESTAMP","startMs":30,"endMs":40}},
                    {"field":"menu","value":"만두","confidence":0.75,"evidence":{"type":"TIMESTAMP","startMs":50,"endMs":60}}
                  ],
                  "missingFields": []
                }
                """);

        // When
        AiCandidateValidationResult result = validator.validate(payload);

        // Then
        assertThat(result.decision()).isEqualTo(Decision.AUTO_BLOCKED);
        assertThat(result.reasonCodes()).contains("MULTIPLE_CANDIDATES");
        assertThat(result.foodCategoryName()).isNull();
        assertThat(result.candidates().get("menu")).extracting(
                AiCandidateValidationResult.Candidate::value).containsExactly("냉면", "만두");
    }

    @Test
    @DisplayName("메뉴 후보가 단일이지만 근거가 UNKNOWN이면 자동 확정하지 않으면서도 후보를 보존한다")
    void validate_메뉴단일후보UNKNOWN근거_자동확정하지않으면서도후보를보존한다() throws Exception {
        // Given
        JsonNode payload = payload("""
                {
                  "resultCompleteness": "COMPLETE",
                  "candidates": [
                    {"field":"restaurantName","value":"맛집","confidence":0.95,"evidence":{"type":"TIMESTAMP","startMs":10,"endMs":20}},
                    {"field":"address","value":"서울시","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":10,"endMs":20}},
                    {"field":"location","value":"마포구","confidence":0.88,"evidence":{"type":"TIMESTAMP","startMs":10,"endMs":20}},
                    {"field":"visitEvidence","value":"직접 방문","confidence":0.91,"evidence":{"type":"TIMESTAMP","startMs":10,"endMs":20}},
                    {"field":"menu","value":"냉면","confidence":0.80,"evidence":{"type":"UNKNOWN"}}
                  ],
                  "missingFields": []
                }
                """);

        // When
        AiCandidateValidationResult result = validator.validate(payload);

        // Then
        assertThat(result.decision()).isEqualTo(Decision.AUTO_BLOCKED);
        assertThat(result.reasonCodes()).contains("UNKNOWN_EVIDENCE");
        assertThat(result.foodCategoryName()).isNull();
        assertThat(result.missingFields()).doesNotContain("menu");
        assertThat(result.candidates().get("menu")).extracting(
                AiCandidateValidationResult.Candidate::value).containsExactly("냉면");
    }

    @Test
    @DisplayName("필수 필드에 후보가 아예 없으면 여전히 missingFields에 남고 차단한다")
    void validate_필수필드후보없음_여전히missingFields에남고차단한다() throws Exception {
        // Given
        JsonNode payload = payload("""
                {
                  "resultCompleteness": "COMPLETE",
                  "candidates": [
                    {"field":"restaurantName","value":"맛집","confidence":0.95,"evidence":{"type":"TIMESTAMP","startMs":10,"endMs":20}},
                    {"field":"location","value":"마포구","confidence":0.88,"evidence":{"type":"TIMESTAMP","startMs":10,"endMs":20}},
                    {"field":"visitEvidence","value":"직접 방문","confidence":0.91,"evidence":{"type":"TIMESTAMP","startMs":10,"endMs":20}}
                  ],
                  "missingFields": []
                }
                """);

        // When
        AiCandidateValidationResult result = validator.validate(payload);

        // Then
        assertThat(result.decision()).isEqualTo(Decision.AUTO_BLOCKED);
        assertThat(result.missingFields()).contains("address");
        assertThat(result.reasonCodes()).contains("MISSING_REQUIRED_FIELD");
        assertThat(result.candidates()).doesNotContainKey("address");
    }

    @Test
    @DisplayName("UNKNOWN 태그는 자동 연결하지 않고 거부 태그로 분리한다")
    void validate_UNKNOWN태그_자동연결하지않고거부태그로분리한다() throws Exception {
        // Given
        JsonNode payload = payloadWithTag("""
                {"field":"tag","candidateTagId":"tag-1","tagType":"MENU","rawLabel":"냉면",
                 "normalizedCode":"MENU_NAENGMYEON","label":"냉면","confidence":0.9,
                 "evidence":{"type":"UNKNOWN"}}
                """);

        // When
        AiCandidateValidationResult result = validator.validate(payload);

        // Then
        assertThat(result.decision()).isEqualTo(Decision.AUTO_CONFIRMED);
        assertThat(result.tags()).isEmpty();
        assertThat(result.rejectedTags()).singleElement().satisfies(tag -> {
            assertThat(tag.decision()).isEqualTo(AiCandidateValidationResult.TagDecision.AUTO_REJECTED);
            assertThat(tag.rejectionReason()).isEqualTo("UNKNOWN_EVIDENCE");
        });
    }

    @Test
    @DisplayName("허용 태그 유형과 위치 근거를 가진 태그만 자동 연결 후보가 된다")
    void validate_유효태그_자동연결후보로분류한다() throws Exception {
        // Given
        JsonNode payload = payloadWithTag("""
                {"field":"tag","candidateTagId":"tag-1","tagType":"TASTE","rawLabel":"담백한",
                 "normalizedCode":"TASTE_LIGHT","label":"담백한","confidence":1.0,
                 "evidence":{"type":"TEXT_RANGE","startOffset":10,"endOffset":20,"sourceHash":"hash-1"}}
                """);

        // When
        AiCandidateValidationResult result = validator.validate(payload);

        // Then
        assertThat(result.decision()).isEqualTo(Decision.AUTO_CONFIRMED);
        assertThat(result.tags()).singleElement().satisfies(tag -> {
            assertThat(tag.isAutoConnectable()).isTrue();
            assertThat(tag.normalizedCode()).isEqualTo("TASTE_LIGHT");
        });
    }

    @Test
    @DisplayName("선택 태그만 누락되면 본문 후보는 자동 확정 가능으로 판정한다")
    void validate_선택태그누락_본문후보는자동확정가능으로판정한다() throws Exception {
        // Given
        JsonNode payload = payload("""
                {
                  "resultCompleteness": "PARTIAL",
                  "candidates": [
                    {"field":"restaurantName","value":"맛집","confidence":0.95,"evidence":{"type":"TIMESTAMP","startMs":100,"endMs":200}},
                    {"field":"address","value":"서울시","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":100,"endMs":200}},
                    {"field":"location","value":"마포구","confidence":0.88,"evidence":{"type":"TIMESTAMP","startMs":100,"endMs":200}},
                    {"field":"visitEvidence","value":"직접 방문","confidence":0.91,"evidence":{"type":"TIMESTAMP","startMs":100,"endMs":200}},
                    {"field":"menu","value":"냉면","confidence":0.80,"evidence":{"type":"TIMESTAMP","startMs":300,"endMs":400}}
                  ],
                  "missingFields": ["tag"]
                }
                """);

        // When
        AiCandidateValidationResult result = validator.validate(payload);

        // Then
        assertThat(result.decision()).isEqualTo(Decision.AUTO_CONFIRMED);
        assertThat(result.missingFields()).containsExactly("tag");
        assertThat(result.reasonCodes()).contains("PARTIAL_RESULT");
    }

    private JsonNode payload(String candidates) throws Exception {
        return objectMapper.readTree(candidates);
    }

    private JsonNode payloadWithTag(String tag) throws Exception {
        return payload("""
                {
                  "resultCompleteness": "COMPLETE",
                  "candidates": [
                    {"field":"restaurantName","value":"맛집","confidence":0.95,"evidence":{"type":"TIMESTAMP","startMs":100,"endMs":200}},
                    {"field":"address","value":"서울시","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":100,"endMs":200}},
                    {"field":"location","value":"마포구","confidence":0.88,"evidence":{"type":"TIMESTAMP","startMs":100,"endMs":200}},
                    {"field":"visitEvidence","value":"직접 방문","confidence":0.91,"evidence":{"type":"TIMESTAMP","startMs":100,"endMs":200}},
                    %s
                  ],
                  "missingFields": []
                }
                """.formatted(tag));
    }
}
