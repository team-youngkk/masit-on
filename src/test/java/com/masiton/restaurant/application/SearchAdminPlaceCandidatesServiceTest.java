package com.masiton.restaurant.application;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.restaurant.application.port.in.SearchAdminPlaceCandidatesUseCase;
import com.masiton.restaurant.application.port.out.PlaceSearchCandidate;
import com.masiton.restaurant.application.port.out.PlaceSearchPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("관리자 장소 검색 애플리케이션 서비스")
class SearchAdminPlaceCandidatesServiceTest {

    private final PlaceSearchPort placeSearchPort = mock(PlaceSearchPort.class);
    private final SearchAdminPlaceCandidatesService service = new SearchAdminPlaceCandidatesService(placeSearchPort);

    @Test
    @DisplayName("name이 없으면 MISSING_REQUIRED_FIELD를 던진다")
    void 검색_name누락_예외를던진다() {
        assertThatThrownBy(() -> service.search(
                new SearchAdminPlaceCandidatesUseCase.SearchAdminPlaceCandidatesCommand(null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD.name());
    }

    @Test
    @DisplayName("name이 100자를 넘으면 INVALID_FIELD_VALUE를 던진다")
    void 검색_name길이위반_예외를던진다() {
        String tooLong = "가".repeat(101);
        assertThatThrownBy(() -> service.search(
                new SearchAdminPlaceCandidatesUseCase.SearchAdminPlaceCandidatesCommand(tooLong, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo(ErrorCode.INVALID_FIELD_VALUE.name());
    }

    @Test
    @DisplayName("name이 유니코드 공백뿐이면 INVALID_FIELD_VALUE를 던지고 외부 검색을 호출하지 않는다")
    void 검색_name유니코드공백_예외를던지고외부검색을호출하지않는다() {
        assertThatThrownBy(() -> service.search(
                new SearchAdminPlaceCandidatesUseCase.SearchAdminPlaceCandidatesCommand("　", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo(ErrorCode.INVALID_FIELD_VALUE.name());

        verifyNoInteractions(placeSearchPort);
    }

    @Test
    @DisplayName("name은 앞뒤 공백을 제거한 뒤 검색한다")
    void 검색_name공백제거_정규화해검색한다() {
        when(placeSearchPort.search(any())).thenReturn(List.of());

        service.search(new SearchAdminPlaceCandidatesUseCase.SearchAdminPlaceCandidatesCommand("  아코  ", null));

        verify(placeSearchPort).search("아코");
    }

    @Test
    @DisplayName("roadAddressHint는 정규화 후 255자까지 허용한다")
    void 검색_roadAddressHint255자_외부검색을호출한다() {
        when(placeSearchPort.search(any())).thenReturn(List.of());

        service.search(new SearchAdminPlaceCandidatesUseCase.SearchAdminPlaceCandidatesCommand(
                "아코", "가".repeat(255)));

        verify(placeSearchPort).search("아코");
    }

    @Test
    @DisplayName("roadAddressHint가 정규화 후 255자를 넘으면 INVALID_FIELD_VALUE를 던지고 외부 검색을 호출하지 않는다")
    void 검색_roadAddressHint256자_예외를던지고외부검색을호출하지않는다() {
        assertThatThrownBy(() -> service.search(
                new SearchAdminPlaceCandidatesUseCase.SearchAdminPlaceCandidatesCommand(
                        "아코", "가".repeat(256))))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo(ErrorCode.INVALID_FIELD_VALUE.name());

        verifyNoInteractions(placeSearchPort);
    }

    @Test
    @DisplayName("카카오 검색이 실패하면 EXTERNAL_SERVICE_ERROR를 던진다")
    void 검색_카카오검색실패_예외를던진다() {
        when(placeSearchPort.search(any())).thenThrow(new PlaceSearchFailedException());

        assertThatThrownBy(() -> service.search(
                new SearchAdminPlaceCandidatesUseCase.SearchAdminPlaceCandidatesCommand("아코", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo(ErrorCode.EXTERNAL_SERVICE_ERROR.name());
    }

    @Test
    @DisplayName("빈 결과는 빈 목록을 반환한다")
    void 검색_후보없음_빈목록을반환한다() {
        when(placeSearchPort.search(any())).thenReturn(List.of());

        List<SearchAdminPlaceCandidatesUseCase.PlaceCandidateResult> results = service.search(
                new SearchAdminPlaceCandidatesUseCase.SearchAdminPlaceCandidatesCommand("없는가게", null));

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("서울 자치구를 뽑을 수 없는 주소는 district를 null로 두고 항목을 남긴다")
    void 검색_자치구추출불가_district를null로남긴다() {
        when(placeSearchPort.search(any())).thenReturn(List.of(
                new PlaceSearchCandidate("부산집", "https://place.map.kakao.com/1", "부산 영도구 태종로99번길 28", null)));

        List<SearchAdminPlaceCandidatesUseCase.PlaceCandidateResult> results = service.search(
                new SearchAdminPlaceCandidatesUseCase.SearchAdminPlaceCandidatesCommand("부산집", null));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).district()).isNull();
        assertThat(results.get(0).phoneNumber()).isNull();
    }

    @Test
    @DisplayName("roadAddressHint가 있으면 더 잘 맞는 후보를 앞에 둔다")
    void 정렬_힌트가있으면_더잘맞는후보를앞에둔다() {
        PlaceSearchCandidate farther = new PlaceSearchCandidate(
                "아코", "https://place.map.kakao.com/1", "서울특별시 마포구 성내동 12-38", "02-000-0000");
        PlaceSearchCandidate closer = new PlaceSearchCandidate(
                "아코", "https://place.map.kakao.com/2", "서울특별시 강동구 성내동 12-38", "02-000-0001");
        when(placeSearchPort.search(any())).thenReturn(List.of(farther, closer));

        List<SearchAdminPlaceCandidatesUseCase.PlaceCandidateResult> results = service.search(
                new SearchAdminPlaceCandidatesUseCase.SearchAdminPlaceCandidatesCommand(
                        "아코", "서울 강동구 성내동 12-38"));

        assertThat(results).extracting(SearchAdminPlaceCandidatesUseCase.PlaceCandidateResult::kakaoPlaceUrl)
                .containsExactly("https://place.map.kakao.com/2", "https://place.map.kakao.com/1");
    }

    @Test
    @DisplayName("roadAddressHint가 없으면 카카오 응답 순서를 그대로 유지한다")
    void 정렬_힌트가없으면_응답순서를유지한다() {
        PlaceSearchCandidate first = new PlaceSearchCandidate(
                "아코", "https://place.map.kakao.com/1", "서울특별시 마포구 성내동 12-38", "02-000-0000");
        PlaceSearchCandidate second = new PlaceSearchCandidate(
                "아코", "https://place.map.kakao.com/2", "서울특별시 강동구 성내동 12-38", "02-000-0001");
        when(placeSearchPort.search(any())).thenReturn(List.of(first, second));

        List<SearchAdminPlaceCandidatesUseCase.PlaceCandidateResult> results = service.search(
                new SearchAdminPlaceCandidatesUseCase.SearchAdminPlaceCandidatesCommand("아코", null));

        assertThat(results).extracting(SearchAdminPlaceCandidatesUseCase.PlaceCandidateResult::kakaoPlaceUrl)
                .containsExactly("https://place.map.kakao.com/1", "https://place.map.kakao.com/2");
    }

    @Test
    @DisplayName("검색은 외부 HTTP 호출이므로 트랜잭션을 열지 않는다")
    void 검색_트랜잭션을열지않는다() throws NoSuchMethodException {
        Method search = SearchAdminPlaceCandidatesService.class.getMethod(
                "search", SearchAdminPlaceCandidatesUseCase.SearchAdminPlaceCandidatesCommand.class);

        assertThat(search.getAnnotation(Transactional.class)).isNull();
        assertThat(SearchAdminPlaceCandidatesService.class.getAnnotation(Transactional.class)).isNull();
    }
}
