package com.masiton.restaurant.application.naturallanguage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.masiton.common.web.BusinessException;
import com.masiton.restaurant.application.port.in.NaturalLanguageInterpretationView;
import com.masiton.restaurant.application.port.in.NaturalLanguageSearchCommand;
import com.masiton.restaurant.application.port.in.NaturalLanguageSearchResult;
import com.masiton.restaurant.application.port.in.RestaurantSearchResult;
import com.masiton.restaurant.application.port.in.SearchRestaurantsCommand;
import com.masiton.restaurant.application.port.in.SearchRestaurantsUseCase;
import com.masiton.restaurant.application.port.out.NaturalLanguageInterpretation;
import com.masiton.restaurant.application.port.out.NaturalLanguageParser;
import com.masiton.restaurant.application.port.out.NaturalLanguageRateLimitPort;
import com.masiton.restaurant.application.port.out.RestaurantSearchQueryPort;

/**
 * 자연어 해석과 기존 맛집 목록 조회를 연결한다.
 * 자연어 검색은 별도 결과·저장소를 만들지 않고 기존 SearchRestaurantsUseCase만 호출한다.
 */
@Service
public class NaturalLanguageSearchService {

    private static final String SAFE_UNSUPPORTED_TEXT = "지원되지 않는 조건";
    private static final String SAFE_UNRESOLVED_TEXT = "해석할 수 없는 조건";

    private final NaturalLanguageParser parser;
    private final NaturalLanguageRateLimitPort rateLimitPort;
    private final RestaurantSearchQueryPort restaurantSearchQueryPort;
    private final SearchRestaurantsUseCase searchRestaurantsUseCase;

    public NaturalLanguageSearchService(
            NaturalLanguageParser parser,
            NaturalLanguageRateLimitPort rateLimitPort,
            RestaurantSearchQueryPort restaurantSearchQueryPort,
            SearchRestaurantsUseCase searchRestaurantsUseCase
    ) {
        this.parser = parser;
        this.rateLimitPort = rateLimitPort;
        this.restaurantSearchQueryPort = restaurantSearchQueryPort;
        this.searchRestaurantsUseCase = searchRestaurantsUseCase;
    }

    public NaturalLanguageSearchResult search(NaturalLanguageSearchCommand command) {
        if (!rateLimitPort.tryAcquire(command.clientAddress())) {
            throw new BusinessException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "NATURAL_LANGUAGE_RATE_LIMITED",
                    "자연어 검색 요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요.",
                    60);
        }
        validateDirectTags(command.tags());
        NaturalLanguageInterpretation parsed = parser.parse(command.sentence());
        if (parsed == null) {
            throw unavailable();
        }
        parsed = excludeInactiveNaturalTags(parsed);

        MergedConditions merged = isSuspiciousInput(parsed)
                ? new MergedConditions(NaturalLanguageInterpretation.AppliedConditions.empty(), List.of())
                : parsed.status() == NaturalLanguageInterpretation.Status.FAILED
                ? new MergedConditions(directConditions(command), List.of())
                : merge(parsed, command);
        RestaurantSearchResult result = merged.conditions().hasAny()
                ? searchRestaurantsUseCase.search(new SearchRestaurantsCommand(
                        merged.conditions().query(),
                        merged.conditions().district(),
                        merged.conditions().category(),
                        merged.conditions().creatorId(),
                        merged.conditions().tags(),
                        command.page(),
                        command.size()))
                : emptyResult(command.page(), command.size());

        return new NaturalLanguageSearchResult(toView(parsed, merged), result);
    }

    private void validateDirectTags(List<String> tags) {
        if (tags.isEmpty()) {
            return;
        }
        if (restaurantSearchQueryPort.findActiveTagCodes(tags).size() != new HashSet<>(tags).size()) {
            throw new BusinessException(
                    com.masiton.common.web.ErrorCode.INVALID_FIELD_VALUE,
                    "filters.tags",
                    "활성 상태의 태그 코드만 사용할 수 있습니다.");
        }
    }

    private NaturalLanguageInterpretation excludeInactiveNaturalTags(NaturalLanguageInterpretation parsed) {
        List<String> tags = parsed.appliedConditions().tags();
        if (tags.isEmpty()) {
            return parsed;
        }
        Set<String> activeTags = restaurantSearchQueryPort.findActiveTagCodes(tags);
        if (activeTags.size() == new HashSet<>(tags).size()) {
            return parsed;
        }
        List<String> applicableTags = tags.stream().filter(activeTags::contains).toList();
        NaturalLanguageInterpretation.AppliedConditions conditions = parsed.appliedConditions();
        NaturalLanguageInterpretation.AppliedConditions filtered = new NaturalLanguageInterpretation.AppliedConditions(
                conditions.query(), conditions.district(), conditions.category(), conditions.creatorId(), applicableTags);
        List<NaturalLanguageInterpretation.IgnoredCondition> ignored = new ArrayList<>(parsed.ignoredConditions());
        ignored.add(new NaturalLanguageInterpretation.IgnoredCondition(
                NaturalLanguageInterpretation.IgnoredCondition.Kind.UNRESOLVED,
                "현재 사용할 수 없는 태그 조건",
                "INACTIVE_TAG"));
        NaturalLanguageInterpretation.Status status = filtered.hasAny()
                ? NaturalLanguageInterpretation.Status.PARTIAL
                : NaturalLanguageInterpretation.Status.FAILED;
        return new NaturalLanguageInterpretation(status, filtered, ignored, parsed.conflicts(), parsed.parserVersion());
    }

    private boolean isSuspiciousInput(NaturalLanguageInterpretation parsed) {
        return parsed.ignoredConditions().stream()
                .anyMatch(condition -> "SUSPICIOUS_INPUT".equals(condition.reason()));
    }

    private MergedConditions merge(NaturalLanguageInterpretation parsed, NaturalLanguageSearchCommand command) {
        NaturalLanguageInterpretation.AppliedConditions natural = parsed.appliedConditions();
        List<NaturalLanguageInterpretation.Conflict> conflicts = new ArrayList<>(parsed.conflicts());

        String query = directOrNatural(command.query(), natural.query(), "query", conflicts);
        String district = directOrNatural(command.district(), natural.district(), "district", conflicts);
        String category = directOrNatural(command.category(), natural.category(), "category", conflicts);
        String creatorId = directOrNatural(command.creatorId(), natural.creatorId(), "creatorId", conflicts);
        List<String> tags = directTagsOrNatural(command.tags(), natural.tags(), conflicts);

        return new MergedConditions(
                new NaturalLanguageInterpretation.AppliedConditions(query, district, category, creatorId, tags),
                conflicts);
    }

    private NaturalLanguageInterpretation.AppliedConditions directConditions(NaturalLanguageSearchCommand command) {
        return new NaturalLanguageInterpretation.AppliedConditions(
                command.query(),
                command.district(),
                command.category(),
                command.creatorId(),
                command.tags());
    }

    private String directOrNatural(
            String direct,
            String natural,
            String field,
            List<NaturalLanguageInterpretation.Conflict> conflicts
    ) {
        if (direct == null) {
            return natural;
        }
        if (natural != null && !Objects.equals(direct, natural)) {
            conflicts.add(new NaturalLanguageInterpretation.Conflict(
                    NaturalLanguageInterpretation.Conflict.Field.valueOf(field),
                    NaturalLanguageInterpretation.Conflict.Resolution.DIRECT_FILTER_WON));
        }
        return direct;
    }

    private List<String> directTagsOrNatural(
            List<String> direct,
            List<String> natural,
            List<NaturalLanguageInterpretation.Conflict> conflicts
    ) {
        if (direct == null || direct.isEmpty()) {
            return natural;
        }
        if (natural != null && !natural.isEmpty()
                && !new HashSet<>(direct).equals(new HashSet<>(natural))) {
            conflicts.add(new NaturalLanguageInterpretation.Conflict(
                    NaturalLanguageInterpretation.Conflict.Field.tags,
                    NaturalLanguageInterpretation.Conflict.Resolution.DIRECT_FILTER_WON));
        }
        return List.copyOf(direct);
    }

    private NaturalLanguageInterpretationView toView(
            NaturalLanguageInterpretation parsed,
            MergedConditions merged
    ) {
        List<NaturalLanguageInterpretationView.IgnoredCondition> ignored = parsed.ignoredConditions().stream()
                .map(condition -> new NaturalLanguageInterpretationView.IgnoredCondition(
                        condition.type().name(),
                        safeConditionText(condition.type()),
                        condition.reason()))
                .toList();
        List<NaturalLanguageInterpretationView.Conflict> conflicts = merged.conflicts().stream()
                .map(conflict -> new NaturalLanguageInterpretationView.Conflict(
                        conflict.field().name(), conflict.resolution().name()))
                .toList();
        NaturalLanguageInterpretation.AppliedConditions conditions = merged.conditions();
        NaturalLanguageInterpretationView.Status status;
        if (parsed.status() == NaturalLanguageInterpretation.Status.FAILED) {
            status = conditions.hasAny()
                    ? NaturalLanguageInterpretationView.Status.PARTIAL
                    : NaturalLanguageInterpretationView.Status.FAILED;
        } else if (parsed.status() == NaturalLanguageInterpretation.Status.APPLIED
                && parsed.ignoredConditions().isEmpty()
                && merged.conflicts().isEmpty()) {
            status = NaturalLanguageInterpretationView.Status.APPLIED;
        } else {
            status = NaturalLanguageInterpretationView.Status.PARTIAL;
        }
        return new NaturalLanguageInterpretationView(
                status,
                new NaturalLanguageInterpretationView.AppliedConditions(
                        conditions.query(),
                        conditions.district(),
                        conditions.category(),
                        conditions.creatorId(),
                        conditions.tags()),
                ignored,
                conflicts,
                parsed.parserVersion());
    }

    private String safeConditionText(NaturalLanguageInterpretation.IgnoredCondition.Kind kind) {
        return kind == NaturalLanguageInterpretation.IgnoredCondition.Kind.UNSUPPORTED
                ? SAFE_UNSUPPORTED_TEXT
                : SAFE_UNRESOLVED_TEXT;
    }

    private RestaurantSearchResult emptyResult(int page, int size) {
        return new RestaurantSearchResult(List.of(), page, size, 0, 0, false);
    }

    private BusinessException unavailable() {
        return new BusinessException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "NATURAL_LANGUAGE_UNAVAILABLE",
                "자연어 해석 구성요소를 사용할 수 없습니다.");
    }

    private record MergedConditions(
            NaturalLanguageInterpretation.AppliedConditions conditions,
            List<NaturalLanguageInterpretation.Conflict> conflicts
    ) {
    }
}
