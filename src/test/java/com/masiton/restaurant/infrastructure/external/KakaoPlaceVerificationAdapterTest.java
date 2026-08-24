package com.masiton.restaurant.infrastructure.external;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.masiton.restaurant.application.PlaceVerificationFailedException;
import com.masiton.restaurant.application.port.out.VerifiedPlace;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 제공자 표기를 도메인 표기로 바꾸는 정규화 규칙을 검증한다.
 *
 * 실제 Kakao는 {@code place_url}을 http로, 도로명주소 시도명을 축약해 준다. 두 표기가
 * WireMock 스텁과 달라 운영 배포에서만 드러난 결함이 있었으므로, 제공자 표기 그대로를
 * 입력으로 두고 판정 결과를 고정한다.
 */
@DisplayName("Kakao 장소 검증 Adapter")
class KakaoPlaceVerificationAdapterTest {

    private static final String PLACE_ID = "327148272";
    private static final URI SUBMITTED_URL = URI.create("https://place.map.kakao.com/" + PLACE_ID);

    private final HttpClient httpClient = mock(HttpClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("place_url이 http여도 후보로 채택하고 https로 정규화한다")
    void 검증_제공자가http를주면_https로정규화한다() throws Exception {
        givenResponse(200, document("http://place.map.kakao.com/" + PLACE_ID, "서울 강남구 언주로93길 22-3"));

        Optional<VerifiedPlace> verified = adapter().verify("서울집", SUBMITTED_URL, "02-501-2126");

        assertThat(verified).isPresent();
        assertThat(verified.get().kakaoPlaceUrl()).isEqualTo("https://place.map.kakao.com/" + PLACE_ID);
    }

    @Test
    @DisplayName("도로명주소의 축약 시도명을 전체 표기로 정규화한다")
    void 검증_축약시도명을주면_전체표기로정규화한다() throws Exception {
        givenResponse(200, document("http://place.map.kakao.com/" + PLACE_ID, "서울 강남구 언주로93길 22-3"));

        Optional<VerifiedPlace> verified = adapter().verify("서울집", SUBMITTED_URL, "02-501-2126");

        assertThat(verified).isPresent();
        assertThat(verified.get().roadAddress()).isEqualTo("서울특별시 강남구 언주로93길 22-3");
    }

    @Test
    @DisplayName("서울 밖 주소는 정규화하지 않고 그대로 넘긴다")
    void 검증_서울밖주소는_그대로넘긴다() throws Exception {
        givenResponse(200, document("https://place.map.kakao.com/" + PLACE_ID, "부산 영도구 태종로99번길 28"));

        Optional<VerifiedPlace> verified = adapter().verify("서울집", SUBMITTED_URL, "051-416-4845");

        assertThat(verified).isPresent();
        assertThat(verified.get().roadAddress()).isEqualTo("부산 영도구 태종로99번길 28");
    }

    @Test
    @DisplayName("http·https가 아닌 scheme은 계약 오류로 처리한다")
    void 경계값_비HTTP스키마를주면_계약오류로처리한다() throws Exception {
        givenResponse(200, document("ftp://place.map.kakao.com/" + PLACE_ID, "서울 강남구 언주로93길 22-3"));

        assertThatThrownBy(() -> adapter().verify("서울집", SUBMITTED_URL, "02-501-2126"))
                .isInstanceOf(PlaceVerificationFailedException.class);
    }

    @Test
    @DisplayName("기본 포트가 붙은 place_url은 포트를 뺀 https로 정규화한다")
    void 경계값_기본포트를주면_포트를빼고정규화한다() throws Exception {
        givenResponse(200, document("http://place.map.kakao.com:80/" + PLACE_ID, "서울 강남구 언주로93길 22-3"));

        Optional<VerifiedPlace> verified = adapter().verify("서울집", SUBMITTED_URL, "02-501-2126");

        assertThat(verified).isPresent();
        assertThat(verified.get().kakaoPlaceUrl()).isEqualTo("https://place.map.kakao.com/" + PLACE_ID);
    }

    @Test
    @DisplayName("https의 기본 포트도 표기에서 뺀다")
    void 경계값_https기본포트를주면_포트를빼고정규화한다() throws Exception {
        givenResponse(200, document("https://place.map.kakao.com:443/" + PLACE_ID, "서울 강남구 언주로93길 22-3"));

        Optional<VerifiedPlace> verified = adapter().verify("서울집", SUBMITTED_URL, "02-501-2126");

        assertThat(verified).isPresent();
        assertThat(verified.get().kakaoPlaceUrl()).isEqualTo("https://place.map.kakao.com/" + PLACE_ID);
    }

    @Test
    @DisplayName("비표준 포트가 붙은 place_url은 계약 오류로 처리한다")
    void 경계값_비표준포트를주면_계약오류로처리한다() throws Exception {
        givenResponse(200, document("http://place.map.kakao.com:8080/" + PLACE_ID, "서울 강남구 언주로93길 22-3"));

        assertThatThrownBy(() -> adapter().verify("서울집", SUBMITTED_URL, "02-501-2126"))
                .isInstanceOf(PlaceVerificationFailedException.class);
    }

    @Test
    @DisplayName("user-info가 붙은 place_url은 계약 오류로 처리한다")
    void 예외_userinfo를주면_계약오류로처리한다() throws Exception {
        givenResponse(200, document("http://attacker@place.map.kakao.com/" + PLACE_ID, "서울 강남구 언주로93길 22-3"));

        assertThatThrownBy(() -> adapter().verify("서울집", SUBMITTED_URL, "02-501-2126"))
                .isInstanceOf(PlaceVerificationFailedException.class);
    }

    @Test
    @DisplayName("host가 다른 place_url은 후보로 채택하지 않는다")
    void 예외_다른host를주면_후보로채택하지않는다() throws Exception {
        givenResponse(200, document("https://place.example.com/" + PLACE_ID, "서울 강남구 언주로93길 22-3"));

        assertThat(adapter().verify("서울집", SUBMITTED_URL, "02-501-2126")).isEmpty();
    }

    @Test
    @DisplayName("path가 다른 place_url은 후보로 채택하지 않는다")
    void 예외_다른path를주면_후보로채택하지않는다() throws Exception {
        givenResponse(200, document("http://place.map.kakao.com/99999999", "서울 강남구 언주로93길 22-3"));

        assertThat(adapter().verify("서울집", SUBMITTED_URL, "02-501-2126")).isEmpty();
    }

    @Test
    @DisplayName("장소 검증은 Kakao Local keyword GET path·query·Authorization 계약을 사용한다")
    void 검증_요청규격_KakaoLocalKeyword계약을따른다() throws Exception {
        givenResponse(200, document("https://place.map.kakao.com/" + PLACE_ID, "서울 강남구 언주로93길 22-3"));

        adapter().verify("서울집", SUBMITTED_URL, "02-501-2126");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = captor.getValue();
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.uri().getPath()).isEqualTo("/v2/local/search/keyword.json");
        assertThat(request.uri().getQuery()).contains("query=서울집");
        assertThat(request.headers().firstValue("Authorization")).hasValue("KakaoAK test-key");
    }

    @Test
    @DisplayName("endpoint가 없거나 HTTP(S) origin이 아니면 HTTP 호출 전에 초기화를 거부한다")
    void 초기화_endpoint누락또는지원하지않는형식_호출전에거부한다() {
        assertThatThrownBy(() -> new KakaoPlaceVerificationAdapter(
                httpClient, objectMapper, "", "test-key"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new KakaoPlaceVerificationAdapter(
                httpClient, objectMapper, "ftp://dapi.kakao.com", "test-key"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new KakaoPlaceVerificationAdapter(
                httpClient, objectMapper, "http://localhost:8081/local", "test-key"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new KakaoPlaceVerificationAdapter(
                httpClient, objectMapper, "http://localhost:8081", "test-key", "https://dapi.kakao.com"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new KakaoPlaceVerificationAdapter(
                httpClient, objectMapper, "https://dapi.kakao.com:8443", "test-key", "https://dapi.kakao.com"))
                .isInstanceOf(IllegalStateException.class);
    }

    private KakaoPlaceVerificationAdapter adapter() {
        return new KakaoPlaceVerificationAdapter(httpClient, objectMapper, "https://dapi.kakao.com", "test-key");
    }

    private void givenResponse(int statusCode, String body) throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }

    private String document(String placeUrl, String roadAddress) {
        return """
                {
                  "documents": [
                    {
                      "id": "%s",
                      "place_name": "서울집",
                      "place_url": "%s",
                      "road_address_name": "%s",
                      "phone": "02-501-2126"
                    }
                  ]
                }
                """.formatted(PLACE_ID, placeUrl, roadAddress);
    }
}
