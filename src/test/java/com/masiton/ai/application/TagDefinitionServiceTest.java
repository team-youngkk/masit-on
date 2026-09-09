package com.masiton.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase.CreateCommand;
import com.masiton.ai.application.port.out.TagDefinitionStore;
import com.masiton.common.web.BusinessException;

@DisplayName("관리자 태그 정의 서비스")
class TagDefinitionServiceTest {
    private final TagDefinitionStore store = mock(TagDefinitionStore.class);
    private final TagDefinitionService service = new TagDefinitionService(store);

    @Test
    @DisplayName("표시명과 별칭은 NFKC·유니코드 공백·대소문자를 정규화해 저장한다")
    void 생성_호환문자와유니코드공백_정규화용어를저장한다() {
        given(store.create(any())).willAnswer(invocation -> {
            TagDefinitionStore.NewTagDefinition value = invocation.getArgument(0);
            assertThat(value.normalizedTerms()).containsExactly("abc 가족", "가족 외식");
            return new com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase.TagDefinition(
                    value.code(), value.type(), value.displayName(), value.aliases(), "ACTIVE", "MANUAL_OVERRIDE", 0);
        });

        var result = service.create(new CreateCommand(
                "OCCASION_FAMILY", "OCCASION", "ＡＢＣ\u00a0가족", List.of(" 가족\u3000외식 ")));

        assertThat(result.code()).isEqualTo("OCCASION_FAMILY");
    }

    @Test
    @DisplayName("요청 내부 정규화 중복과 금지 표현을 400으로 거절한다")
    void 생성_중복또는금지용어_400으로거절한다() {
        assertThatThrownBy(() -> service.create(new CreateCommand(
                "MENU_FAMILY", "MENU", "ＡＢＣ", List.of("abc"))))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(400);
                    assertThat(error.code()).isEqualTo("INVALID_FIELD_VALUE");
                });
        assertThatThrownBy(() -> service.create(new CreateCommand(
                "OCCASION_OPEN", "OCCASION", "방문 가능", List.of())))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(400);
                    assertThat(error.code()).isEqualTo("TAG_TERM_FORBIDDEN");
                });
    }

    @Test
    @DisplayName("표시명과 별칭의 제어 문자와 실행성 문자를 400으로 거절한다")
    void 생성_안전하지않은표시텍스트_400으로거절한다() {
        assertThatThrownBy(() -> service.create(new CreateCommand(
                "OCCASION_FAMILY", "OCCASION", "가족\n모임", List.of())))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("INVALID_FIELD_VALUE"));
        assertThatThrownBy(() -> service.create(new CreateCommand(
                "OCCASION_FAMILY", "OCCASION", "가족 모임", List.of("<가족>"))))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("INVALID_FIELD_VALUE"));
    }

    @Test
    @DisplayName("코드와 정규화 용어 저장 충돌을 구분된 409로 반환한다")
    void 생성_DB중복_코드와용어충돌을구분한다() {
        doThrow(new TagDefinitionStore.DuplicateCodeException()).when(store).create(any());
        assertThatThrownBy(() -> service.create(validCommand()))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("TAG_CODE_ALREADY_EXISTS"));

        reset(store);
        doThrow(new TagDefinitionStore.DuplicateTermException(new RuntimeException())).when(store).create(any());
        assertThatThrownBy(() -> service.create(validCommand()))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("TAG_TERM_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("원문 길이가 허용 범위여도 NFKC 결과가 200자를 넘으면 400으로 거절한다")
    void 생성_NFKC확장후길이초과_400으로거절한다() {
        String compatibilityLigatures = "\ufdfa".repeat(100);

        assertThat(compatibilityLigatures).hasSize(100);
        assertThat(TagTermNormalizer.normalize(compatibilityLigatures).length()).isGreaterThan(200);
        assertThatThrownBy(() -> service.create(new CreateCommand(
                "MENU_EXPANDED", "MENU", compatibilityLigatures, List.of())))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(400);
                    assertThat(error.code()).isEqualTo("INVALID_FIELD_VALUE");
                    assertThat(error.fieldErrors()).extracting(field -> field.field())
                            .containsExactly("displayName");
                });
    }

    private CreateCommand validCommand() {
        return new CreateCommand("OCCASION_FAMILY", "OCCASION", "가족 모임", List.of("가족 외식"));
    }
}
