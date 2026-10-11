package com.masiton.restaurant.application.naturallanguage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.masiton.restaurant.domain.model.Region;

/** 활성 마스터의 부모 문맥과 동명 후보를 함께 판정한다. */
final class NaturalLanguageRegionResolver {

    private static final Map<String, String> PROVINCE_ALIASES = Map.ofEntries(
            Map.entry("서울특별시", "서울"), Map.entry("부산광역시", "부산"),
            Map.entry("대구광역시", "대구"), Map.entry("인천광역시", "인천"),
            Map.entry("광주광역시", "광주"), Map.entry("대전광역시", "대전"),
            Map.entry("울산광역시", "울산"), Map.entry("세종특별자치시", "세종"),
            Map.entry("경기도", "경기"), Map.entry("강원특별자치도", "강원"),
            Map.entry("충청북도", "충북"), Map.entry("충청남도", "충남"),
            Map.entry("전북특별자치도", "전북"), Map.entry("전라남도", "전남"),
            Map.entry("경상북도", "경북"), Map.entry("경상남도", "경남"),
            Map.entry("제주특별자치도", "제주"));
    private static final Pattern UNKNOWN_GEOGRAPHY = Pattern.compile(
            "(?<![\\p{L}\\p{N}])((?:[가-힣]{1,}(?:구|군|동)|[가-힣]{2,}(?:시|도|지역))(?=$|\\s|에서|의|에)|[가-힣]{2,}(?=에서))");
    private final Map<String, Set<Region>> aliases = new LinkedHashMap<>();

    NaturalLanguageRegionResolver(List<Region> master) {
        Set<UUID> parents = master.stream().filter(Region::isActive).filter(region -> region.getParentId() == null)
                .map(Region::getId).collect(java.util.stream.Collectors.toSet());
        for (Region region : master) {
            if (!region.isActive() || region.getAdministrativeCode() == null
                    || (region.getParentId() != null && !parents.contains(region.getParentId()))) {
                continue;
            }
            add(region.getName(), region);
            if (region.getParentId() == null) {
                String alias = PROVINCE_ALIASES.get(region.getName());
                if (alias != null) {
                    add(alias, region);
                }
                if (region.getName().equals("전남광주통합특별시")) {
                    add("전남광주", region);
                }
            } else if (region.getName().length() > 2) {
                String stem = region.getName().replaceFirst("[시군구]$", "");
                if (!PROVINCE_ALIASES.containsValue(stem)) {
                    add(stem, region);
                }
            }
        }
        NaturalLanguageDictionary.standard().aliasesFor(ConditionField.DISTRICT).forEach((alias, names) -> {
            if (names.size() != 1) {
                return;
            }
            String name = names.iterator().next();
            // 자치구 이름의 축약은 전국 후보를 유지하고 서울 동네 별칭만 추가한다.
            if (alias.equals(name) || alias.equals(name.replaceFirst("[시군구]$", ""))) {
                return;
            }
            master.stream().filter(Region::isActive).filter(region -> region.getName().equals(name))
                    .filter(region -> master.stream().anyMatch(parent -> parent.isActive()
                            && parent.getName().equals("서울특별시") && parent.getId().equals(region.getParentId())))
                    .filter(region -> region.getAdministrativeCode() != null).forEach(region -> add(alias, region));
        });
    }

    private void add(String alias, Region region) {
        aliases.computeIfAbsent(NaturalLanguageTermNormalizer.normalize(alias), ignored -> new LinkedHashSet<>())
                .add(region);
    }

    Resolution resolve(String sentence, List<String> otherConditionAliases) {
        boolean[] occupied = new boolean[sentence.length()];
        List<Set<Region>> matches = new ArrayList<>();
        List<String> matchedAliases = new ArrayList<>();
        aliases.entrySet().stream().sorted(Map.Entry.comparingByKey(
                Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder())))
                .forEach(entry -> {
                    for (var range : NaturalLanguageRestaurantParser.findAliasMatches(sentence, entry.getKey())) {
                        if (endsAtBoundary(sentence, range.end())
                                && !NaturalLanguageRestaurantParser.overlaps(occupied, range)) {
                            NaturalLanguageRestaurantParser.occupy(occupied, range);
                            matches.add(entry.getValue());
                            matchedAliases.add(entry.getKey());
                        }
                    }
                });
        char[] remainder = sentence.toCharArray();
        for (int index = 0; index < occupied.length; index++) {
            if (occupied[index]) {
                remainder[index] = ' ';
            }
        }
        for (String alias : otherConditionAliases) {
            for (var range : NaturalLanguageRestaurantParser.findAliasMatches(sentence, alias)) {
                java.util.Arrays.fill(remainder, range.start(), range.end(), ' ');
            }
        }
        boolean unknown = UNKNOWN_GEOGRAPHY.matcher(new String(remainder)).find();
        if (!unknown) {
            unknown = java.util.stream.Stream.concat(PROVINCE_ALIASES.keySet().stream(),
                            PROVINCE_ALIASES.values().stream())
                    .anyMatch(alias -> NaturalLanguageRestaurantParser.findAliasMatches(sentence, alias).stream()
                            .anyMatch(range -> endsAtBoundary(sentence, range.end())
                                    && !NaturalLanguageRestaurantParser.overlaps(occupied, range)));
        }
        if (unknown) {
            return new Resolution(null, true, matchedAliases);
        }
        Set<Region> roots = new LinkedHashSet<>();
        List<Set<Region>> children = new ArrayList<>();
        for (Set<Region> candidates : matches) {
            if (candidates.stream().allMatch(region -> region.getParentId() == null)) {
                roots.addAll(candidates);
            } else {
                children.add(candidates);
            }
        }
        if (roots.size() > 1) {
            return new Resolution(null, true, matchedAliases);
        }
        Region root = roots.stream().findFirst().orElse(null);
        Set<Region> resolvedChildren = new LinkedHashSet<>();
        for (Set<Region> candidates : children) {
            List<Region> contextual = candidates.stream().filter(region -> root == null
                    || root.getId().equals(region.getParentId())).toList();
            if (contextual.size() != 1) {
                return new Resolution(null, true, matchedAliases);
            }
            resolvedChildren.add(contextual.getFirst());
        }
        if (resolvedChildren.size() > 1) {
            return new Resolution(null, true, matchedAliases);
        }
        Region selected = resolvedChildren.stream().findFirst().orElse(root);
        return new Resolution(selected == null ? null : selected.getAdministrativeCode(), false, matchedAliases);
    }

    record Resolution(String code, boolean unresolved, List<String> aliases) {
    }

    private static boolean endsAtBoundary(String sentence, int end) {
        if (end == sentence.length() || !Character.isLetterOrDigit(sentence.codePointAt(end))) {
            return true;
        }
        return sentence.substring(end).matches("^(에서|에|의|은|는|가|을|를|과|와)(?=$|\\s).*" );
    }
}
