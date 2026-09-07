package com.masiton.ai.application;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase;
import com.masiton.ai.application.port.out.TagDefinitionStore;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.common.web.SafeTextPolicy;

@Service
public class TagDefinitionService implements ManageTagDefinitionsUseCase {
    private static final Set<String> TYPES = Set.of("MENU", "TASTE", "OCCASION", "ATMOSPHERE");
    private static final Pattern CODE = Pattern.compile(
            "^(MENU|TASTE|OCCASION|ATMOSPHERE)_[A-Z0-9]+(?:_[A-Z0-9]+)*$");
    private static final Set<String> FORBIDDEN_TERMS = Set.of(
            "가격", "품질", "평점", "영업시간", "영업", "방문가능", "예약",
            "price", "quality", "rating", "hours", "availability", "promotion");

    private final TagDefinitionStore store;

    public TagDefinitionService(TagDefinitionStore store) {
        this.store = store;
    }

    @Override
    @Transactional(readOnly = true)
    public Result listActive() {
        return new Result(store.findActive());
    }

    @Override
    @Transactional
    public TagDefinition create(CreateCommand command) {
        ValidatedTag tag = validate(command);
        try {
            return store.create(new TagDefinitionStore.NewTagDefinition(
                    UUID.randomUUID(), tag.code(), tag.type(), tag.displayName(), tag.aliases(),
                    tag.normalizedTerms(), OffsetDateTime.now()));
        } catch (TagDefinitionStore.DuplicateCodeException exception) {
            throw new BusinessException(HttpStatus.CONFLICT, "TAG_CODE_ALREADY_EXISTS",
                    "Tag code already exists.");
        } catch (TagDefinitionStore.DuplicateTermException exception) {
            throw new BusinessException(HttpStatus.CONFLICT, "TAG_TERM_ALREADY_EXISTS",
                    "Tag term already exists.");
        }
    }

    private ValidatedTag validate(CreateCommand command) {
        if (command == null) {
            throw invalid("request");
        }
        String code = trimmed(command.code());
        String type = trimmed(command.type());
        String displayName = SafeTextPolicy.requireSafe(command.displayName(), "displayName");
        List<String> aliases = command.aliases() == null ? List.of()
                : command.aliases().stream().map(alias -> SafeTextPolicy.requireSafe(alias, "aliases")).toList();
        if (!TYPES.contains(type) || code.length() < 3 || code.length() > 64 || !CODE.matcher(code).matches()
                || !code.startsWith(type + "_") || displayName.isEmpty()
                || displayName.length() > TagTermNormalizer.MAX_RAW_LENGTH
                || aliases.size() > 20 || aliases.stream().anyMatch(
                        alias -> alias.isEmpty() || alias.length() > TagTermNormalizer.MAX_RAW_LENGTH)) {
            throw invalid("tagDefinition");
        }
        String normalizedDisplayName = TagTermNormalizer.normalize(displayName);
        List<String> normalizedAliases = aliases.stream().map(TagTermNormalizer::normalize).toList();
        if (normalizedDisplayName.length() > TagTermNormalizer.MAX_NORMALIZED_LENGTH) {
            throw invalid("displayName");
        }
        if (normalizedAliases.stream().anyMatch(
                alias -> alias.length() > TagTermNormalizer.MAX_NORMALIZED_LENGTH)) {
            throw invalid("aliases");
        }
        List<String> normalizedTerms = java.util.stream.Stream.concat(
                java.util.stream.Stream.of(normalizedDisplayName), normalizedAliases.stream()).toList();
        if (normalizedTerms.stream().anyMatch(String::isBlank)
                || new HashSet<>(normalizedTerms).size() != normalizedTerms.size()) {
            throw invalid("aliases");
        }
        if (normalizedTerms.stream().anyMatch(this::isForbidden)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "TAG_TERM_FORBIDDEN",
                    "Tag term is forbidden.");
        }
        return new ValidatedTag(code, type, displayName, List.copyOf(aliases), normalizedTerms);
    }

    private boolean isForbidden(String value) {
        String compact = value.replace(" ", "");
        return FORBIDDEN_TERMS.stream().anyMatch(compact::contains);
    }

    private String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private BusinessException invalid(String field) {
        return new BusinessException(ErrorCode.INVALID_FIELD_VALUE, field, "Invalid tag definition value.");
    }

    private record ValidatedTag(String code, String type, String displayName, List<String> aliases,
                                List<String> normalizedTerms) {
    }
}
