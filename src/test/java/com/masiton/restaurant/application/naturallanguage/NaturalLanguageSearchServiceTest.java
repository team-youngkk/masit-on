package com.masiton.restaurant.application.naturallanguage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.restaurant.application.port.in.NaturalLanguageInterpretationView;
import com.masiton.restaurant.application.port.in.NaturalLanguageSearchCommand;
import com.masiton.restaurant.application.port.in.RestaurantSearchResult;
import com.masiton.restaurant.application.port.in.SearchRestaurantsUseCase;
import com.masiton.restaurant.application.port.out.ActiveTagDictionaryPort;
import com.masiton.restaurant.application.port.out.ActiveTagDictionaryPort.ActiveTagDictionarySnapshot;
import com.masiton.restaurant.application.port.out.ActiveTagDictionaryPort.TagDefinition;
import com.masiton.restaurant.application.port.out.NaturalLanguageRateLimitPort;
import com.masiton.restaurant.application.port.out.RestaurantSearchQueryPort;

@DisplayName("자연어 검색 서비스의 동적 태그 충돌 병합")
class NaturalLanguageSearchServiceTest {

    private final NaturalLanguageRateLimitPort rateLimitPort = mock(NaturalLanguageRateLimitPort.class);
    private final RestaurantSearchQueryPort restaurantSearchQueryPort = mock(RestaurantSearchQueryPort.class);
    private final SearchRestaurantsUseCase searchRestaurantsUseCase = mock(SearchRestaurantsUseCase.class);

    @BeforeEach
    void setUp() {
        given(rateLimitPort.tryAcquire(any())).willReturn(true);
        given(restaurantSearchQueryPort.findActiveTagCodes(any())).willAnswer(invocation ->
                Set.copyOf(invocation.<List<String>>getArgument(0)));
        given(searchRestaurantsUseCase.search(any())).willReturn(emptyResult());
    }

    @Test
    @DisplayName("별칭 충돌로 PARTIAL이어도 직접 tags를 적용하고 충돌을 반환한다")
    void search_별칭충돌PARTIAL_직접tags우선충돌을반환한다() {
        NaturalLanguageSearchService service = service(List.of(
                new TagDefinition("TAG_A", List.of("특별한")),
                new TagDefinition("TAG_B", List.of("특별한"))));

        var result = service.search(command("강남에서 특별한 태그 맛집", List.of("DIRECT_TAG")));

        assertThat(result.interpretation().status()).isEqualTo(NaturalLanguageInterpretationView.Status.PARTIAL);
        assertThat(result.interpretation().appliedConditions().district()).isEqualTo("강남구");
        assertThat(result.interpretation().appliedConditions().tags()).containsExactly("DIRECT_TAG");
        assertThat(result.interpretation().conflicts()).containsExactly(
                new NaturalLanguageInterpretationView.Conflict("tags", "DIRECT_FILTER_WON"));
    }

    @Test
    @DisplayName("태그 6개로 FAILED여도 직접 tags를 적용하고 충돌을 반환한다")
    void search_태그6개FAILED_직접tags우선충돌을반환한다() {
        List<TagDefinition> definitions = new ArrayList<>();
        for (int index = 1; index <= 6; index++) {
            definitions.add(new TagDefinition("TAG_" + index, List.of("태그값" + index)));
        }
        NaturalLanguageSearchService service = service(definitions);

        var result = service.search(command(
                "태그값1 태그값2 태그값3 태그값4 태그값5 태그값6 맛집",
                List.of("DIRECT_TAG")));

        assertThat(result.interpretation().status()).isEqualTo(NaturalLanguageInterpretationView.Status.PARTIAL);
        assertThat(result.interpretation().appliedConditions().tags()).containsExactly("DIRECT_TAG");
        assertThat(result.interpretation().conflicts()).containsExactly(
                new NaturalLanguageInterpretationView.Conflict("tags", "DIRECT_FILTER_WON"));
    }

    @Test
    @DisplayName("다른 필드만 미해석이면 직접 tags 충돌로 오인하지 않는다")
    void search_query만미해석_직접tags충돌은추가하지않는다() {
        NaturalLanguageSearchService service = service(List.of());

        var result = service.search(command("'첫 번째'와 '두 번째' 맛집", List.of("DIRECT_TAG")));

        assertThat(result.interpretation().status()).isEqualTo(NaturalLanguageInterpretationView.Status.PARTIAL);
        assertThat(result.interpretation().appliedConditions().tags()).containsExactly("DIRECT_TAG");
        assertThat(result.interpretation().conflicts()).isEmpty();
    }

    private NaturalLanguageSearchService service(List<TagDefinition> definitions) {
        ActiveTagDictionaryPort dictionaryPort = () -> new ActiveTagDictionarySnapshot(definitions);
        NaturalLanguageParserAdapter parser = new NaturalLanguageParserAdapter(List::of, dictionaryPort);
        return new NaturalLanguageSearchService(
                parser, rateLimitPort, restaurantSearchQueryPort, searchRestaurantsUseCase);
    }

    private static NaturalLanguageSearchCommand command(String sentence, List<String> tags) {
        return new NaturalLanguageSearchCommand(
                sentence, null, null, null, null, tags, 1, 21, "127.0.0.1");
    }

    private static RestaurantSearchResult emptyResult() {
        return new RestaurantSearchResult(List.of(), 1, 21, 0, 0, false);
    }
}
