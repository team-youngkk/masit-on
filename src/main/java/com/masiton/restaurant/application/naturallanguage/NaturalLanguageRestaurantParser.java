package com.masiton.restaurant.application.naturallanguage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 외부 Provider 없이 P1 규칙·사전으로 자연어를 기존 맛집 검색 조건으로 변환한다.
 * 이 클래스는 저장소, Entity, 기존 목록 UseCase를 직접 호출하지 않는다.
 */
public final class NaturalLanguageRestaurantParser {

    public static final String PARSER_VERSION = "P1";

    private static final Pattern QUOTED_QUERY = Pattern.compile("[\\\"'“‘]([^\\\"'”’]{1,100})[\\\"'”’]");
    private static final Pattern CREATOR_ID = Pattern.compile(
            "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b");
    private static final Pattern SUSPICIOUS_INPUT = Pattern.compile(
            "(?i)(ignore\\s+(all\\s+)?previous|system\\s+prompt|developer\\s+message|jailbreak|프롬프트|이전\\s*지시|지시를?\\s*무시)");
    private static final Pattern UNSUPPORTED_CONDITION = Pattern.compile(
            "(?i)(분위기\\s*좋|가성비|저렴|비싸|가격|평점|별점|영업|예약|주차|배달|포장|현재\\s*위치|추천|인기|무조건|할인|거리|반경|오픈)");
    private static final List<String> IGNORED_WORDS = List.of(
            "에서", "의", "이", "가", "은", "는", "을", "를", "한", "방문", "방문한", "있는", "있고", "찾아", "줘", "주세요",
            "집", "식당", "맛집", "조건", "태그", "그리고", "또는", "및", "좋은", "좋고");

    private final NaturalLanguageDictionary dictionary;

    public NaturalLanguageRestaurantParser() {
        this(NaturalLanguageDictionary.standard());
    }

    public NaturalLanguageRestaurantParser(NaturalLanguageDictionary dictionary) {
        this.dictionary = java.util.Objects.requireNonNull(dictionary);
    }

    public NaturalLanguageParseResult parse(String sentence) {
        return parse(sentence, NaturalLanguageFilters.empty());
    }

    public NaturalLanguageParseResult parse(String sentence, NaturalLanguageFilters directFilters) {
        String normalizedSentence = normalizeSentence(sentence);
        if (SUSPICIOUS_INPUT.matcher(normalizedSentence).find()) {
            return failedForSuspiciousInput();
        }
        NaturalLanguageFilters direct = directFilters == null ? NaturalLanguageFilters.empty() : directFilters;
        List<IgnoredCondition> ignored = new ArrayList<>();
        List<NaturalLanguageConflict> conflicts = new ArrayList<>();
        EnumMap<ConditionField, Extraction> extractions = extractFields(normalizedSentence, ignored);
        NaturalLanguageFilters parsed = toParsedFilters(extractions);
        MergeState merged = merge(parsed, extractions, direct, ignored, conflicts);
        addUnsupportedSummary(normalizedSentence, extractions, ignored);

        boolean hasAppliedNaturalCondition = merged.appliedNaturalFields().stream()
                .anyMatch(field -> field != ConditionField.TAGS || !merged.appliedNaturalTags().isEmpty());
        InterpretationStatus status;
        if (hasAppliedNaturalCondition && ignored.isEmpty()) {
            status = InterpretationStatus.APPLIED;
        } else if (hasAppliedNaturalCondition || !conflicts.isEmpty()) {
            status = InterpretationStatus.PARTIAL;
        } else {
            status = InterpretationStatus.FAILED;
        }

        NaturalLanguageInterpretation interpretation = new NaturalLanguageInterpretation(
                status,
                parsed,
                merged.appliedFilters(),
                ignored,
                conflicts,
                PARSER_VERSION);
        return new NaturalLanguageParseResult(interpretation);
    }

    private NaturalLanguageParseResult failedForSuspiciousInput() {
        NaturalLanguageInterpretation interpretation = new NaturalLanguageInterpretation(
                InterpretationStatus.FAILED,
                NaturalLanguageFilters.empty(),
                NaturalLanguageFilters.empty(),
                List.of(new IgnoredCondition(
                        IgnoredConditionType.UNSUPPORTED, "지원하지 않는 입력", "SUSPICIOUS_INPUT")),
                List.of(),
                PARSER_VERSION);
        return new NaturalLanguageParseResult(interpretation);
    }

    private EnumMap<ConditionField, Extraction> extractFields(
            String sentence,
            List<IgnoredCondition> ignored) {
        EnumMap<ConditionField, Extraction> result = new EnumMap<>(ConditionField.class);
        for (ConditionField field : ConditionField.values()) {
            if (field == ConditionField.TAGS) {
                result.put(field, extractTags(sentence, ignored));
            } else if (field == ConditionField.QUERY) {
                result.put(field, extractQuery(sentence, ignored));
            } else if (field == ConditionField.CREATOR_ID) {
                result.put(field, extractCreatorId(sentence, ignored));
            } else {
                result.put(field, extractScalar(field, sentence, ignored));
            }
        }
        return result;
    }

    private Extraction extractScalar(ConditionField field, String sentence, List<IgnoredCondition> ignored) {
        Map<String, Set<String>> aliases = dictionary.aliasesFor(field);
        Set<String> values = new LinkedHashSet<>();
        List<String> matchedAliases = new ArrayList<>();
        aliases.entrySet().stream()
                .sorted(Map.Entry.<String, Set<String>>comparingByKey(Comparator.comparingInt(String::length).reversed()))
                .forEach(entry -> {
                    if (containsAlias(sentence, entry.getKey())) {
                        matchedAliases.add(entry.getKey());
                        values.addAll(entry.getValue());
                    }
                });
        Extraction extraction = new Extraction(field, values, matchedAliases, values.size() > 1, values.size());
        if (extraction.ambiguous()) {
            ignored.add(unresolved(field));
        }
        return extraction;
    }

    private Extraction extractCreatorId(String sentence, List<IgnoredCondition> ignored) {
        Extraction aliases = extractCreatorAliases(sentence, ignored);
        Matcher matcher = CREATOR_ID.matcher(sentence);
        Set<String> values = new LinkedHashSet<>(aliases.values());
        List<String> matches = new ArrayList<>(aliases.matchedAliases());
        while (matcher.find()) {
            String value = matcher.group();
            values.add(value);
            matches.add(value);
        }
        boolean ambiguous = aliases.ambiguous() || values.size() > 1;
        if (ambiguous && !aliases.ambiguous() && values.size() > 1) {
            ignored.add(unresolved(ConditionField.CREATOR_ID));
        }
        return new Extraction(ConditionField.CREATOR_ID, values, matches, ambiguous, values.size());
    }

    private Extraction extractCreatorAliases(String sentence, List<IgnoredCondition> ignored) {
        Map<String, Set<String>> aliases = dictionary.aliasesFor(ConditionField.CREATOR_ID);
        Set<String> values = new LinkedHashSet<>();
        List<String> matchedAliases = new ArrayList<>();
        aliases.entrySet().stream()
                .sorted(Map.Entry.<String, Set<String>>comparingByKey(Comparator.comparingInt(String::length).reversed()))
                .forEach(entry -> {
                    if (containsAlias(sentence, entry.getKey())
                            && isCreatorAliasConnectedToContext(sentence, entry.getKey())) {
                        matchedAliases.add(entry.getKey());
                        values.addAll(entry.getValue());
                    }
                });
        Extraction extraction = new Extraction(
                ConditionField.CREATOR_ID, values, matchedAliases, values.size() > 1, values.size());
        if (extraction.ambiguous()) {
            ignored.add(unresolved(ConditionField.CREATOR_ID));
        }
        return extraction;
    }

    private static boolean isCreatorAliasConnectedToContext(String sentence, String alias) {
        String compactSentence = compact(sentence);
        String compactAlias = compact(alias);
        int start = compactSentence.indexOf(compactAlias);
        while (start >= 0) {
            String before = compactSentence.substring(0, start);
            String after = compactSentence.substring(start + compactAlias.length());
            boolean prefixed = before.endsWith("유튜버")
                    || before.endsWith("채널")
                    || before.endsWith("크리에이터")
                    || before.endsWith("먹방")
                    || before.endsWith("방문")
                    || before.endsWith("방문한");
            boolean suffixed = after.matches("^(이|가|은|는|을|를)?(방문|다녀|찾|소개|추천).*");
            if (prefixed || suffixed) {
                return true;
            }
            start = compactSentence.indexOf(compactAlias, start + 1);
        }
        return false;
    }

    private Extraction extractTags(String sentence, List<IgnoredCondition> ignored) {
        Map<String, Set<String>> aliases = dictionary.aliasesFor(ConditionField.TAGS);
        Set<String> values = new LinkedHashSet<>();
        List<String> matchedAliases = new ArrayList<>();
        boolean ambiguous = false;
        aliases.entrySet().stream()
                .sorted(Map.Entry.<String, Set<String>>comparingByKey(Comparator.comparingInt(String::length).reversed()))
                .forEach(entry -> {
                    if (containsAlias(sentence, entry.getKey())) {
                        matchedAliases.add(entry.getKey());
                        values.addAll(entry.getValue());
                    }
                });
        for (String alias : matchedAliases) {
            if (aliases.get(alias).size() > 1) {
                ambiguous = true;
                break;
            }
        }
        Extraction extraction = new Extraction(ConditionField.TAGS, values, matchedAliases, ambiguous, values.size());
        if (ambiguous) {
            ignored.add(unresolved(ConditionField.TAGS));
        }
        return extraction;
    }

    private Extraction extractQuery(String sentence, List<IgnoredCondition> ignored) {
        Extraction dictionaryQuery = extractScalar(ConditionField.QUERY, sentence, ignored);
        Matcher matcher = QUOTED_QUERY.matcher(sentence);
        List<String> quoted = new ArrayList<>();
        while (matcher.find()) {
            String value = cleanText(matcher.group(1));
            if (!value.isEmpty()) {
                quoted.add(value);
            }
        }
        if (quoted.size() > 1 || (dictionaryQuery.matched() && !quoted.isEmpty())) {
            ignored.add(unresolved(ConditionField.QUERY));
            return new Extraction(ConditionField.QUERY, Set.of(), quoted, true, quoted.size());
        }
        if (quoted.size() == 1) {
            return new Extraction(ConditionField.QUERY, Set.of(quoted.get(0)), quoted, false, 1);
        }
        return dictionaryQuery;
    }

    private NaturalLanguageFilters toParsedFilters(EnumMap<ConditionField, Extraction> extractions) {
        return new NaturalLanguageFilters(
                scalarValue(extractions.get(ConditionField.QUERY)),
                scalarValue(extractions.get(ConditionField.DISTRICT)),
                scalarValue(extractions.get(ConditionField.CATEGORY)),
                scalarValue(extractions.get(ConditionField.CREATOR_ID)),
                tagValues(extractions.get(ConditionField.TAGS)));
    }

    private MergeState merge(
            NaturalLanguageFilters parsed,
            EnumMap<ConditionField, Extraction> extractions,
            NaturalLanguageFilters direct,
            List<IgnoredCondition> ignored,
            List<NaturalLanguageConflict> conflicts) {
        EnumSet<ConditionField> appliedNaturalFields = EnumSet.noneOf(ConditionField.class);
        Set<String> appliedNaturalTags = new LinkedHashSet<>();

        String query = mergeScalar(ConditionField.QUERY, parsed.query(), direct.query(), extractions, direct, ignored,
                conflicts, appliedNaturalFields);
        String district = mergeScalar(ConditionField.DISTRICT, parsed.district(), direct.district(), extractions, direct,
                ignored, conflicts, appliedNaturalFields);
        String category = mergeScalar(ConditionField.CATEGORY, parsed.category(), direct.category(), extractions, direct,
                ignored, conflicts, appliedNaturalFields);
        String creatorId = mergeScalar(ConditionField.CREATOR_ID, parsed.creatorId(), direct.creatorId(), extractions,
                direct, ignored, conflicts, appliedNaturalFields);

        List<String> tags;
        if (extractions.get(ConditionField.TAGS).ambiguous()) {
            if (!direct.tags().isEmpty()) {
                addConflict(ConditionField.TAGS, direct, ignored, conflicts);
            }
            tags = direct.tags();
        } else if (!direct.tags().isEmpty() && !parsed.tags().isEmpty()
                && !new LinkedHashSet<>(direct.tags()).equals(new LinkedHashSet<>(parsed.tags()))) {
            addConflict(ConditionField.TAGS, direct, ignored, conflicts);
            tags = direct.tags();
        } else if (!direct.tags().isEmpty()) {
            if (!parsed.tags().isEmpty()) {
                appliedNaturalFields.add(ConditionField.TAGS);
                appliedNaturalTags.addAll(parsed.tags());
            }
            tags = direct.tags();
        } else {
            tags = parsed.tags();
            if (!tags.isEmpty()) {
                appliedNaturalFields.add(ConditionField.TAGS);
                appliedNaturalTags.addAll(tags);
            }
        }

        NaturalLanguageFilters applied = new NaturalLanguageFilters(query, district, category, creatorId, tags);
        return new MergeState(applied, appliedNaturalFields, appliedNaturalTags);
    }

    private String mergeScalar(
            ConditionField field,
            String natural,
            String direct,
            EnumMap<ConditionField, Extraction> extractions,
            NaturalLanguageFilters directFilters,
            List<IgnoredCondition> ignored,
            List<NaturalLanguageConflict> conflicts,
            EnumSet<ConditionField> appliedNaturalFields) {
        if (direct != null) {
            if (natural != null && !direct.equals(natural)) {
                addConflict(field, directFilters, ignored, conflicts);
            } else if (extractions.get(field).ambiguous()) {
                addConflict(field, directFilters, ignored, conflicts);
            } else if (natural != null) {
                appliedNaturalFields.add(field);
            }
            return direct;
        }
        if (extractions.get(field).ambiguous()) {
            return null;
        }
        if (natural != null) {
            appliedNaturalFields.add(field);
        }
        return natural;
    }

    private void addConflict(
            ConditionField field,
            NaturalLanguageFilters direct,
            List<IgnoredCondition> ignored,
            List<NaturalLanguageConflict> conflicts) {
        conflicts.add(new NaturalLanguageConflict(field, ConflictResolution.DIRECT_FILTER_WON));
        ignored.add(new IgnoredCondition(IgnoredConditionType.CONFLICT, safeSummary("자연어 " + fieldName(field) + " 조건"),
                "DIRECT_FILTER_WON"));
    }

    private void addUnsupportedSummary(
            String sentence,
            EnumMap<ConditionField, Extraction> extractions,
            List<IgnoredCondition> ignored) {
        if (sentence.isEmpty()) {
            return;
        }
        Matcher unsupported = UNSUPPORTED_CONDITION.matcher(sentence);
        if (unsupported.find()) {
            ignored.add(new IgnoredCondition(
                    IgnoredConditionType.UNSUPPORTED,
                    safeSummary(unsupported.group()),
                    "UNSUPPORTED_CONDITION"));
            return;
        }
        boolean hasAnyMatch = extractions.values().stream().anyMatch(Extraction::matched);
        if (!hasAnyMatch || hasUnknownTagPhrase(sentence, extractions.get(ConditionField.TAGS))) {
            ignored.add(new IgnoredCondition(
                    IgnoredConditionType.UNSUPPORTED,
                    hasAnyMatch ? "해석할 수 없는 태그 조건" : "지원하지 않는 조건",
                    hasAnyMatch ? "UNRESOLVED_VALUE" : "UNSUPPORTED_CONDITION"));
        }
    }

    private boolean hasUnknownTagPhrase(String sentence, Extraction tagExtraction) {
        int tagMarker = sentence.indexOf("태그");
        if (tagMarker < 0) {
            return false;
        }
        String beforeMarker = sentence.substring(0, tagMarker);
        String compactBeforeMarker = compact(beforeMarker);
        for (String token : beforeMarker.split(" ")) {
            String candidate = cleanText(token).replaceAll("[과와및,]$", "");
            if (candidate.isEmpty() || IGNORED_WORDS.stream().anyMatch(candidate::equals)) {
                continue;
            }
            String compactCandidate = compact(candidate);
            boolean known = Arrays.stream(ConditionField.values())
                    .flatMap(field -> dictionary.aliasesFor(field).keySet().stream())
                    .anyMatch(alias -> compactCandidate.contains(alias) || alias.contains(compactCandidate));
            if (!known && !compactBeforeMarker.equals(compactCandidate)) {
                return true;
            }
        }
        return !tagExtraction.matched() && !beforeMarker.isBlank();
    }

    private static IgnoredCondition unresolved(ConditionField field) {
        return new IgnoredCondition(
                IgnoredConditionType.UNRESOLVED,
                safeSummary("여러 후보로 해석되는 " + fieldName(field) + " 조건"),
                "UNRESOLVED_VALUE");
    }

    private static String scalarValue(Extraction extraction) {
        return extraction.ambiguous() || extraction.values().isEmpty() ? null : extraction.values().iterator().next();
    }

    private static List<String> tagValues(Extraction extraction) {
        return extraction.ambiguous() ? List.of() : List.copyOf(extraction.values());
    }

    private static boolean containsAlias(String sentence, String alias) {
        return compact(sentence).contains(alias);
    }

    private static String normalizeSentence(String sentence) {
        return cleanText(sentence);
    }

    private static String cleanText(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
    }

    private static String compact(String value) {
        return cleanText(value).replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static String safeSummary(String value) {
        String summary = cleanText(value)
                .replaceAll("(?i)(https?://|www\\.)\\S+", "[마스킹]")
                .replaceAll("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}", "[마스킹]")
                .replaceAll("\\d{5,}", "***");
        if (summary.isEmpty()) {
            return "지원하지 않는 조건";
        }
        if (summary.codePointCount(0, summary.length()) <= 80) {
            return summary;
        }
        int end = summary.offsetByCodePoints(0, 77);
        return summary.substring(0, end) + "...";
    }

    private static String fieldName(ConditionField field) {
        return switch (field) {
            case QUERY -> "맛집명";
            case DISTRICT -> "지역";
            case CATEGORY -> "카테고리";
            case CREATOR_ID -> "유튜버";
            case TAGS -> "태그";
        };
    }

    private record Extraction(
            ConditionField field,
            Set<String> values,
            List<String> matchedAliases,
            boolean ambiguous,
            int recognizedCount) {

        private Extraction {
            values = Collections.unmodifiableSet(new LinkedHashSet<>(values));
            matchedAliases = List.copyOf(matchedAliases);
        }

        private boolean matched() {
            return !matchedAliases.isEmpty();
        }
    }

    private record MergeState(
            NaturalLanguageFilters appliedFilters,
            EnumSet<ConditionField> appliedNaturalFields,
            Set<String> appliedNaturalTags) {

        private MergeState {
            appliedNaturalFields = appliedNaturalFields.clone();
            appliedNaturalTags = Collections.unmodifiableSet(new LinkedHashSet<>(appliedNaturalTags));
        }
    }
}
