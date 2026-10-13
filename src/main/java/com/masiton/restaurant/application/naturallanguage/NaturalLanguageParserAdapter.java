package com.masiton.restaurant.application.naturallanguage;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.masiton.common.web.BusinessException;
import com.masiton.creator.application.port.in.CreatorSelectionItem;
import com.masiton.creator.application.port.in.GetPublicCreatorSelectionListUseCase;
import com.masiton.restaurant.application.port.out.ActiveTagDictionaryPort;
import com.masiton.restaurant.application.port.out.ActiveTagDictionaryUnavailableException;
import com.masiton.restaurant.application.port.out.NaturalLanguageInterpretation;
import com.masiton.restaurant.application.port.out.NaturalLanguageParser;
import com.masiton.restaurant.application.port.out.RegionRepositoryPort;
import org.springframework.dao.DataAccessException;

/** 운영 P2와 역사적 P1 회귀 경로를 Restaurant Application의 해석기 Port에 연결한다. */
@Component
public final class NaturalLanguageParserAdapter implements NaturalLanguageParser {

    private final NaturalLanguageRestaurantParser delegate;
    private final GetPublicCreatorSelectionListUseCase creatorSelectionListUseCase;
    private final ActiveTagDictionaryPort activeTagDictionaryPort;
    private final RegionRepositoryPort regionRepositoryPort;

    public NaturalLanguageParserAdapter() {
        this(new NaturalLanguageRestaurantParser(), null, null);
    }

    public NaturalLanguageParserAdapter(GetPublicCreatorSelectionListUseCase creatorSelectionListUseCase) {
        this(new NaturalLanguageRestaurantParser(), creatorSelectionListUseCase, null);
    }

    public NaturalLanguageParserAdapter(
            GetPublicCreatorSelectionListUseCase creatorSelectionListUseCase,
            ActiveTagDictionaryPort activeTagDictionaryPort
    ) {
        this(new NaturalLanguageRestaurantParser(), creatorSelectionListUseCase, activeTagDictionaryPort);
    }

    @Autowired
    public NaturalLanguageParserAdapter(
            GetPublicCreatorSelectionListUseCase creatorSelectionListUseCase,
            ActiveTagDictionaryPort activeTagDictionaryPort,
            RegionRepositoryPort regionRepositoryPort) {
        this.delegate = null;
        this.creatorSelectionListUseCase = java.util.Objects.requireNonNull(creatorSelectionListUseCase);
        this.activeTagDictionaryPort = java.util.Objects.requireNonNull(activeTagDictionaryPort);
        this.regionRepositoryPort = java.util.Objects.requireNonNull(regionRepositoryPort);
    }

    NaturalLanguageParserAdapter(NaturalLanguageRestaurantParser delegate) {
        this(delegate, null, null);
    }

    private NaturalLanguageParserAdapter(
            NaturalLanguageRestaurantParser delegate,
            GetPublicCreatorSelectionListUseCase creatorSelectionListUseCase,
            ActiveTagDictionaryPort activeTagDictionaryPort) {
        this.delegate = delegate;
        this.creatorSelectionListUseCase = creatorSelectionListUseCase;
        this.activeTagDictionaryPort = activeTagDictionaryPort;
        this.regionRepositoryPort = null;
    }

    @Override
    public NaturalLanguageInterpretation parse(String sentence) {
        NaturalLanguageRestaurantParser parser = createParser();
        NaturalLanguageInterpretation source = convert(parser.parse(sentence).interpretation());
        return source;
    }

    private NaturalLanguageRestaurantParser createParser() {
        if (creatorSelectionListUseCase == null || activeTagDictionaryPort == null) {
            return creatorSelectionListUseCase == null
                    ? delegate
                    : new NaturalLanguageRestaurantParser(NaturalLanguageDictionary.standard(creatorAliases()));
        }
        try {
            Map<String, Collection<String>> tagTerms = new TreeMap<>();
            activeTagDictionaryPort.getActiveTagDictionary().definitions()
                    .forEach(definition -> tagTerms.put(definition.code(), definition.terms()));
            if (regionRepositoryPort != null) {
                return new NaturalLanguageRestaurantParser(
                        NaturalLanguageDictionary.national(creatorAliases(), tagTerms),
                        new NaturalLanguageRegionResolver(regionRepositoryPort.findAllActive()));
            }
            return new NaturalLanguageRestaurantParser(
                    NaturalLanguageDictionary.standard(creatorAliases(), tagTerms));
        } catch (ActiveTagDictionaryUnavailableException | DataAccessException exception) {
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "NATURAL_LANGUAGE_UNAVAILABLE",
                    "자연어 해석 구성요소를 사용할 수 없습니다.");
        }
    }

    private Map<String, String> creatorAliases() {
        return creatorSelectionListUseCase.getPublicSelectionList().stream()
                .collect(java.util.stream.Collectors.toMap(
                        item -> item.id().toString(),
                        CreatorSelectionItem::channelName,
                        (first, ignored) -> first));
    }

    private NaturalLanguageInterpretation convert(
            com.masiton.restaurant.application.naturallanguage.NaturalLanguageInterpretation source
    ) {
        return new NaturalLanguageInterpretation(
                NaturalLanguageInterpretation.Status.valueOf(source.status().name()),
                convert(source.appliedConditions()),
                source.ignoredConditions().stream()
                        .map(this::convert)
                        .toList(),
                source.conflicts().stream()
                        .map(this::convert)
                        .toList(),
                source.unresolvedFields().stream()
                        .map(this::convert)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                source.parserVersion());
    }

    private NaturalLanguageInterpretation.AppliedConditions convert(NaturalLanguageFilters source) {
        return new NaturalLanguageInterpretation.AppliedConditions(
                source.query(),
                source.district(),
                source.category(),
                source.creatorId(),
                source.tags(),
                source.regionCode());
    }

    private NaturalLanguageInterpretation.IgnoredCondition convert(IgnoredCondition source) {
        return new NaturalLanguageInterpretation.IgnoredCondition(
                NaturalLanguageInterpretation.IgnoredCondition.Kind.valueOf(source.type().name()),
                source.text(),
                source.reason());
    }

    private NaturalLanguageInterpretation.Conflict convert(NaturalLanguageConflict source) {
        return new NaturalLanguageInterpretation.Conflict(
                NaturalLanguageInterpretation.Conflict.Field.valueOf(toPortField(source.field())),
                NaturalLanguageInterpretation.Conflict.Resolution.valueOf(source.resolution().name()));
    }

    private NaturalLanguageInterpretation.Conflict.Field convert(ConditionField source) {
        return NaturalLanguageInterpretation.Conflict.Field.valueOf(toPortField(source));
    }

    private String toPortField(ConditionField field) {
        return switch (field) {
            case QUERY -> "query";
            case DISTRICT -> "district";
            case REGION_CODE -> "regionCode";
            case CATEGORY -> "category";
            case CREATOR_ID -> "creatorId";
            case TAGS -> "tags";
        };
    }
}
