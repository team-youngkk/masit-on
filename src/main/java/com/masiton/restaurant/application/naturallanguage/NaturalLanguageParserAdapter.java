package com.masiton.restaurant.application.naturallanguage;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import com.masiton.creator.application.port.in.CreatorSelectionItem;
import com.masiton.creator.application.port.in.GetPublicCreatorSelectionListUseCase;
import com.masiton.restaurant.application.port.out.NaturalLanguageInterpretation;
import com.masiton.restaurant.application.port.out.NaturalLanguageParser;

/** P1 parser 담당 구현을 Restaurant Application의 해석기 Port에 연결하는 Adapter다. */
@Component
public final class NaturalLanguageParserAdapter implements NaturalLanguageParser {

    private final NaturalLanguageRestaurantParser delegate;
    private final GetPublicCreatorSelectionListUseCase creatorSelectionListUseCase;

    public NaturalLanguageParserAdapter() {
        this(new NaturalLanguageRestaurantParser(), null);
    }

    @Autowired
    public NaturalLanguageParserAdapter(GetPublicCreatorSelectionListUseCase creatorSelectionListUseCase) {
        this(new NaturalLanguageRestaurantParser(), creatorSelectionListUseCase);
    }

    NaturalLanguageParserAdapter(NaturalLanguageRestaurantParser delegate) {
        this(delegate, null);
    }

    private NaturalLanguageParserAdapter(
            NaturalLanguageRestaurantParser delegate,
            GetPublicCreatorSelectionListUseCase creatorSelectionListUseCase) {
        this.delegate = delegate;
        this.creatorSelectionListUseCase = creatorSelectionListUseCase;
    }

    @Override
    public NaturalLanguageInterpretation parse(String sentence) {
        NaturalLanguageRestaurantParser parser = creatorSelectionListUseCase == null
                ? delegate
                : new NaturalLanguageRestaurantParser(NaturalLanguageDictionary.standard(
                        creatorSelectionListUseCase.getPublicSelectionList().stream()
                                .collect(java.util.stream.Collectors.toMap(
                                        item -> item.id().toString(),
                                        CreatorSelectionItem::channelName,
                                        (first, ignored) -> first))));
        NaturalLanguageInterpretation source = convert(parser.parse(sentence).interpretation());
        return source;
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
                source.parserVersion());
    }

    private NaturalLanguageInterpretation.AppliedConditions convert(NaturalLanguageFilters source) {
        return new NaturalLanguageInterpretation.AppliedConditions(
                source.query(),
                source.district(),
                source.category(),
                source.creatorId(),
                source.tags());
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

    private String toPortField(ConditionField field) {
        return switch (field) {
            case QUERY -> "query";
            case DISTRICT -> "district";
            case CATEGORY -> "category";
            case CREATOR_ID -> "creatorId";
            case TAGS -> "tags";
        };
    }
}
