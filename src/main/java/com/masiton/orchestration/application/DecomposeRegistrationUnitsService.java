package com.masiton.orchestration.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.masiton.orchestration.application.port.in.DecomposeRegistrationUnitsUseCase;

import tools.jackson.databind.JsonNode;

/**
 * {@code BR-AIEXTRACT-001} 등록 단위 분해를 수행한다.
 *
 * <h2>후보 필드 간 결속 해석</h2>
 *
 * <p>Gemini 응답 Schema({@code S2})와 {@code ai_candidate_snapshot.candidate_fields} 저장 형태
 * (데이터 계약 5절)는 필드마다 독립된 후보 배열을 반환한다. 한 영상에 맛집이 여러 곳 등장해
 * {@code restaurantName}에 후보가 둘 이상 남아도, 그중 두 번째 후보가 {@code address}의 몇 번째
 * 후보와 같은 물리적 등록 단위에 속하는지를 표시하는 공유 식별자({@code unitIndex}·{@code groupId}
 * 같은 키)가 응답 Schema와 저장 계약 어디에도 없다. {@link com.masiton.ai.infrastructure.provider.config
 * .GeminiHttpVideoExtractionAdapter}의 시스템 지시와 결과 Schema, {@code candidate_fields} 저장
 * 규칙 모두 필드별 배열 순서 이상의 명시적 상관관계 계약을 정의하지 않는다.</p>
 *
 * <p>이 서비스는 그 공백을 새 스키마나 컬럼을 추가해 메우지 않고(범위 밖의 계약 변경이므로),
 * 이 도메인에서 반복되는 원칙인 "임의로 하나를 고르지 않는다"({@code BR-AIEXTRACT-001})와
 * 가장 잘 들어맞는 다음 해석을 채택한다.</p>
 *
 * <ul>
 *   <li><b>인덱스 위치 결속.</b> {@code restaurantName}의 i번째 후보는, 다른 필드도 후보가
 *   둘 이상이면 그 필드의 i번째 후보와 결속한다. Gemini가 한 응답 안에서 같은 장소의 필드들을
 *   같은 상대 순서로 나열한다는 관찰에 기반한 최선의 근사이며, 모델이 실제로 이 순서를
 *   보장한다는 계약은 없다.</li>
 *   <li><b>단일 후보 필드의 균일 적용.</b> 어떤 필드에 후보가 정확히 1건뿐이면(즉
 *   {@code candidate_fields}의 값이 배열이 아니라 문자열이면) 그 값은 등록 단위마다 다른
 *   값으로 나뉘지 않고 모든 등록 단위에 동일하게 적용한다. 예: 한 영상에 맛집 후보가 둘이고
 *   주소 후보가 하나뿐이면(같은 지역 여러 맛집), 그 주소 후보를 두 등록 단위 모두의 주소로 쓴다.</li>
 *   <li><b>결속 실패는 결측으로 처리한다.</b> 어떤 필드에 후보가 둘 이상이지만 그 배열 길이가
 *   {@code restaurantName} 후보 수보다 짧아 i번째 자리에 대응하는 값이 없으면, 짐작으로 채우지
 *   않고 그 등록 단위의 그 필드를 {@code null}(결측)로 남긴다.</li>
 * </ul>
 *
 * <p>이 해석은 관리자 화면에서 검토 가능하도록 판정 결과에 그대로 반영된다. 후보 상관관계
 * 계약이 명시적으로 정의되면(Prompt·Schema 버전 상향과 함께) 이 서비스를 그 계약에 맞춰
 * 교체해야 한다.</p>
 */
@Service
class DecomposeRegistrationUnitsService implements DecomposeRegistrationUnitsUseCase {

    private static final String RESTAURANT_NAME = "restaurantName";
    private static final String ADDRESS = "address";
    private static final String MENU = "menu";
    private static final String VISIT_EVIDENCE = "visitEvidence";

    @Override
    public List<RegistrationUnitBundle> decompose(DecomposeRegistrationUnitsCommand command) {
        Objects.requireNonNull(command, "command");
        JsonNode fields = command.candidateFields();
        JsonNode confidences = command.fieldConfidences();
        JsonNode evidence = command.evidence();

        List<CandidateEntry> nameCandidates = readCandidates(fields, confidences, evidence, RESTAURANT_NAME);
        if (nameCandidates.isEmpty()) {
            return List.of();
        }

        List<RegistrationUnitBundle> bundles = new ArrayList<>(nameCandidates.size());
        for (int index = 0; index < nameCandidates.size(); index++) {
            int unitIndex = index + 1;
            BoundCandidate restaurantName = toBound(nameCandidates.get(index));
            BoundCandidate address = bind(fields, confidences, evidence, ADDRESS, index);
            BoundCandidate menu = bind(fields, confidences, evidence, MENU, index);
            BoundCandidate visitEvidence = bind(fields, confidences, evidence, VISIT_EVIDENCE, index);
            bundles.add(new RegistrationUnitBundle(unitIndex, restaurantName, address, menu, visitEvidence));
        }
        return List.copyOf(bundles);
    }

    private BoundCandidate bind(JsonNode fields, JsonNode confidences, JsonNode evidence, String field, int index) {
        List<CandidateEntry> candidates = readCandidates(fields, confidences, evidence, field);
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return toBound(candidates.get(0));
        }
        if (index >= candidates.size()) {
            return null;
        }
        return toBound(candidates.get(index));
    }

    private List<CandidateEntry> readCandidates(JsonNode fields, JsonNode confidences, JsonNode evidence,
                                                String field) {
        if (fields == null) {
            return List.of();
        }
        JsonNode value = fields.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return List.of();
        }
        if (value.isArray()) {
            List<CandidateEntry> entries = new ArrayList<>();
            for (JsonNode item : value) {
                String itemValue = item.path("value").isTextual() ? item.path("value").textValue() : null;
                if (itemValue == null || itemValue.isBlank()) {
                    continue;
                }
                entries.add(new CandidateEntry(itemValue, toConfidence(item.path("confidence")),
                        item.path("evidence")));
            }
            return List.copyOf(entries);
        }
        if (value.isTextual() && !value.textValue().isBlank()) {
            BigDecimal confidence = confidences == null ? null : toConfidence(confidences.path(field));
            JsonNode fieldEvidence = evidence == null ? null : evidence.path(field);
            return List.of(new CandidateEntry(value.textValue(), confidence, fieldEvidence));
        }
        return List.of();
    }

    private BigDecimal toConfidence(JsonNode node) {
        return node != null && node.isNumber() ? BigDecimal.valueOf(node.doubleValue()) : null;
    }

    private BoundCandidate toBound(CandidateEntry entry) {
        return new BoundCandidate(entry.value(), entry.confidence(), entry.evidence());
    }

    private record CandidateEntry(String value, BigDecimal confidence, JsonNode evidence) {
    }
}
