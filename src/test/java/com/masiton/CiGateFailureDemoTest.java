package com.masiton;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 품질 게이트가 실패한 테스트를 실제로 병합 차단으로 연결하는지 확인하기 위한 일회성 테스트다.
 *
 * <p>의도적으로 실패하며 병합 대상이 아니다. 확인이 끝나면 이 파일과 브랜치를 삭제한다.
 * PR #63 리뷰에서 "의도적 실패 PR이 실제로 병합 불가인지 검증해 주세요"라는 요청에 대응한다.
 */
@DisplayName("품질 게이트 실패 차단 확인용 일회성 테스트")
class CiGateFailureDemoTest {

    @Test
    @DisplayName("의도적으로 실패해 필수 상태 검사를 빨간 상태로 만든다")
    void 의도적실패_필수상태검사_빨간상태가된다() {
        assertThat("품질 게이트 차단 확인용 의도적 실패")
                .as("이 단정은 실패해야 정상이다")
                .isEqualTo("이 값과 일치하지 않는다");
    }
}
