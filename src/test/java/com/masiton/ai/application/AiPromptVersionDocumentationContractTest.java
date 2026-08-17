package com.masiton.ai.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prompt 버전 상향이 현재 운영 계약을 서술하는 문서와 설정 전체로 전파됐는지 고정한다.
 * PR #204에서 P1에서 P2로 올릴 때, PR #220에서 P6으로 올릴 때 같은 누락이 반복돼 검사로 옮겼다.
 * 역사적 이력 표기는 과거 버전을 그대로 남겨야 하므로 "현재" 선언과 기계 판독 값만 검사한다.
 */
@DisplayName("현재 Prompt 버전 문서 계약")
class AiPromptVersionDocumentationContractTest {

    /** "현재" 뒤 60자 안의 첫 번째 P 버전 토큰. `P6-01`, `P2-04` 같은 체크포인트 ID는 제외한다. */
    private static final Pattern CURRENT_DECLARATION = Pattern.compile(
            "현재[^\\n|]{0,60}?\\bP(\\d+)\\b(?![\\d\\-])");
    private static final Pattern API_EXAMPLE = Pattern.compile("\"promptVersion\"\\s*:\\s*\"(P\\d+)\"");
    private static final Pattern APPLICATION_YML = Pattern.compile("prompt-version:\\s*(P\\d+)");

    private static final List<Path> CONTRACT_DOCUMENTS = List.of(
            Path.of("docs/00-overview/scope.md"),
            Path.of("docs/02-analysis/third-expansion-domain-boundaries.md"),
            Path.of("docs/04-product/prd/admin/ai-video-information-extraction.md"),
            Path.of("docs/05-specs/data/data-traceability.md"),
            Path.of("docs/05-specs/data/third-expansion-ai-video-data-contract.md"),
            Path.of("docs/07-adr/adr-backlog.md"),
            Path.of("docs/07-adr/adr-index.md"),
            Path.of("docs/07-adr/adr-traceability.md"),
            Path.of("docs/07-adr/integration/ai-001-video-extraction-candidate-boundary.md"),
            Path.of("docs/08-planning/third-expansion-baseline-review.md"),
            Path.of("docs/08-planning/third-expansion-evaluation-strategy.md"),
            Path.of("docs/08-planning/third-expansion-implementation-plan.md"),
            Path.of("docs/08-planning/third-expansion-scope-and-terminology.md"),
            Path.of("docs/08-planning/third-expansion-task-breakdown.md"));

    @Test
    @DisplayName("현재 계약을 서술하는 문서의 Prompt 버전이 실행 계약 상수와 일치한다")
    void 문서_현재계약선언_실행상수와일치한다() throws IOException {
        List<String> mismatches = new ArrayList<>();

        for (Path document : CONTRACT_DOCUMENTS) {
            assertThat(document).as("계약 문서 경로").exists();
            String text = Files.readString(document, StandardCharsets.UTF_8);
            Matcher declaration = CURRENT_DECLARATION.matcher(text);
            while (declaration.find()) {
                String declared = "P" + declaration.group(1);
                if (!AiExtractionContract.PROMPT_VERSION.equals(declared)) {
                    mismatches.add("%s: %s".formatted(document, declaration.group().replaceAll("\\s+", " ")));
                }
            }
        }

        assertThat(mismatches)
                .as("현재 Prompt 버전은 %s여야 한다. 역사적 이력은 '기존'으로 구분해 적는다.",
                        AiExtractionContract.PROMPT_VERSION)
                .isEmpty();
    }

    @Test
    @DisplayName("API 응답 예시와 애플리케이션 설정의 Prompt 버전이 실행 계약 상수와 일치한다")
    void 설정과API예시_Prompt버전_실행상수와일치한다() throws IOException {
        String apiSpec = Files.readString(
                Path.of("docs/05-specs/api/admin/ai-video-extraction-api.md"), StandardCharsets.UTF_8);
        String applicationYml = Files.readString(
                Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);

        assertThat(matches(API_EXAMPLE, apiSpec))
                .as("API 응답 예시의 promptVersion")
                .isNotEmpty()
                .containsOnly(AiExtractionContract.PROMPT_VERSION);
        assertThat(matches(APPLICATION_YML, applicationYml))
                .as("application.yml의 prompt-version")
                .isNotEmpty()
                .containsOnly(AiExtractionContract.PROMPT_VERSION);
    }

    private List<String> matches(Pattern pattern, String text) {
        return pattern.matcher(text).results().map(result -> result.group(1)).toList();
    }

    @Test
    @DisplayName("계약 문서 목록이 현재 계약을 서술하는 문서를 빠뜨리지 않는다")
    void 계약문서목록_현재계약선언문서를모두포함한다() throws IOException {
        List<Path> declaringDocuments;
        try (Stream<Path> paths = Files.walk(Path.of("docs"))) {
            declaringDocuments = paths
                    .filter(path -> path.toString().endsWith(".md"))
                    .filter(path -> !path.startsWith(Path.of("docs", "troubleshooting")))
                    .filter(this::declaresCurrentPromptVersion)
                    .toList();
        }

        assertThat(declaringDocuments)
                .as("현재 Prompt 버전을 선언하는 문서는 모두 검사 목록에 있어야 한다")
                .allMatch(CONTRACT_DOCUMENTS::contains);
    }

    private boolean declaresCurrentPromptVersion(Path path) {
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            Matcher declaration = CURRENT_DECLARATION.matcher(text);
            while (declaration.find()) {
                if (declaration.group().contains("Prompt") || declaration.group().contains("/S1")) {
                    return true;
                }
            }
            return false;
        } catch (IOException exception) {
            throw new IllegalStateException("문서를 읽을 수 없다: " + path, exception);
        }
    }
}
