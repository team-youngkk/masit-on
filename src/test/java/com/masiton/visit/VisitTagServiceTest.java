package com.masiton.visit;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.masiton.common.web.BusinessException;
import com.masiton.visit.application.VisitTagService;
import com.masiton.visit.application.port.in.ManageVisitTagsUseCase.Change;
import com.masiton.visit.application.port.out.VisitTagStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DisplayName("방문 태그 입력 검증")
class VisitTagServiceTest {
    private final VisitTagStore store = mock(VisitTagStore.class);
    private final VisitTagService service = new VisitTagService(store);

    @Test
    @DisplayName("중복 코드와 빈 사유 및 초과 개수는 저장소 호출 전에 거절한다")
    void 수정_잘못된입력_저장소를호출하지않는다() {
        // Given
        String id = UUID.randomUUID().toString();
        List<Change> invalid = List.of(new Change("v", List.of("A", "A"), "사유"),
                new Change("v", List.of(), "  "), new Change("", List.of(), "사유"),
                new Change("v", java.util.stream.IntStream.range(0, 51).mapToObj(i -> "T" + i).toList(), "사유"));
        // When / Then
        for (Change change : invalid) {
            assertThatThrownBy(() -> service.replace(id, id, change, id))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.code()).isEqualTo("INVALID_FIELD_VALUE"));
        }
        verifyNoInteractions(store);
    }

    @Test
    @DisplayName("잘못된 식별자는 조회 저장소 호출 전에 400으로 거절한다")
    void 조회_잘못된식별자_400으로거절한다() {
        // Given / When / Then
        assertThatThrownBy(() -> service.list("not-an-id"))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.code()).isEqualTo("INVALID_IDENTIFIER");
                    assertThat(e.status().value()).isEqualTo(400);
                });
        verifyNoInteractions(store);
    }
}
