package com.masiton.ai.application;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
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
    private static final Set<String> STATUSES = Set.of("ACTIVE", "DEPRECATED");
    private static final Set<Integer> PAGE_SIZES = Set.of(10, 20, 50);
    private static final Pattern CODE = Pattern.compile("^(MENU|TASTE|OCCASION|ATMOSPHERE)_[A-Z0-9]+(?:_[A-Z0-9]+)*$");
    private static final Set<String> FORBIDDEN_TERMS = Set.of("가격", "품질", "평점", "영업시간", "영업", "방문가능", "예약", "price", "quality", "rating", "hours", "availability", "promotion");
    private final TagDefinitionStore store;

    public TagDefinitionService(TagDefinitionStore store) { this.store = store; }

    @Override @Transactional(readOnly = true)
    public Result listActive() { return new Result(store.findActive()); }

    @Override @Transactional
    public TagDefinition create(CreateCommand command) {
        ValidatedTag tag = validateCreate(command);
        try {
            return store.create(new TagDefinitionStore.NewTagDefinition(UUID.randomUUID(), tag.code(), tag.type(), tag.displayName(), tag.aliases(), tag.normalizedTerms(), OffsetDateTime.now()));
        } catch (TagDefinitionStore.DuplicateCodeException e) { throw conflict("TAG_CODE_ALREADY_EXISTS", "Tag code already exists."); }
        catch (TagDefinitionStore.DuplicateTermException e) { throw conflict("TAG_TERM_ALREADY_EXISTS", "Tag term already exists."); }
    }

    @Override @Transactional(readOnly = true)
    public ManagementResult list(String status, int page, int size) {
        String filter = status == null || status.isBlank() ? "ALL" : status.trim();
        if (!(filter.equals("ALL") || STATUSES.contains(filter)) || page < 1 || !PAGE_SIZES.contains(size)) throw invalid("page");
        long total = store.count(filter);
        int totalPages = (int) Math.ceil((double) total / size);
        long offset = ((long) page - 1) * size;
        return new ManagementResult(store.find(filter, offset, size), new Page(page, size, total, totalPages, page < totalPages));
    }

    @Override @Transactional(readOnly = true)
    public TagDefinition get(String code) { validateCode(code); return callStore(() -> store.get(code)); }

    @Override @Transactional
    public TagDefinition update(String code, UpdateCommand command, String memberId) {
        validateCode(code);
        if (command == null || command.expectedVersion() == null || command.expectedVersion() < 0) throw invalid("expectedVersion");
        if (command.aliases() == null) throw invalid("aliases");
        ValidatedTerms terms = validateTerms(command.displayName(), command.aliases());
        return change(new TagDefinitionStore.Change(code, command.expectedVersion(), terms.displayName(), terms.aliases(), terms.normalizedTerms(), null, "UPDATE", reason(command.reason()), identifier(memberId), OffsetDateTime.now()));
    }

    @Override @Transactional
    public TagDefinition changeStatus(String code, StatusCommand command, String memberId) {
        validateCode(code);
        if (command == null || command.expectedVersion() == null || command.expectedVersion() < 0 || !STATUSES.contains(trimmed(command.status()))) throw invalid("status");
        String status = command.status().trim();
        return change(new TagDefinitionStore.Change(code, command.expectedVersion(), null, null, List.of(), status, status.equals("ACTIVE") ? "REACTIVATE" : "DEPRECATE", reason(command.reason()), identifier(memberId), OffsetDateTime.now()));
    }

    @Override @Transactional(readOnly = true)
    public HistoryResult history(String code, int page, int size) {
        validateCode(code);
        if (page < 1 || !PAGE_SIZES.contains(size)) throw invalid("page");
        get(code);
        long total = store.historyCount(code);
        int totalPages = (int) Math.ceil((double) total / size);
        long offset = ((long) page - 1) * size;
        return new HistoryResult(store.history(code, offset, size), new Page(page, size, total, totalPages, page < totalPages));
    }

    @Override @Transactional(readOnly = true)
    public MergePreview previewMerge(String sourceCode, String targetCode) {
        validateCode(sourceCode);
        validateCode(targetCode);
        if (sourceCode.equals(targetCode)) throw invalid("targetCode");
        try { return store.previewMerge(sourceCode, targetCode); }
        catch (TagDefinitionStore.NotFoundException e) { throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND); }
        catch (TagDefinitionStore.InvalidMergeException e) {
            if ("targetCode".equals(e.code())) throw invalid("targetCode");
            throw conflict("TAG_DEFINITION_MERGE_CONFLICT", "Tag definitions cannot be merged in their current state.");
        }
    }

    @Override @Transactional
    public MergeResult merge(String sourceCode, MergeCommand command, String memberId) {
        validateCode(sourceCode);
        if (command == null) throw invalid("request");
        validateCode(command.targetCode());
        if (sourceCode.equals(command.targetCode())) throw invalid("targetCode");
        if (command.expectedSourceVersion() == null || command.expectedSourceVersion() < 0) throw invalid("expectedSourceVersion");
        if (command.expectedTargetVersion() == null || command.expectedTargetVersion() < 0) throw invalid("expectedTargetVersion");
        String fingerprint = trimmed(command.previewFingerprint());
        if (!fingerprint.matches("^[0-9a-f]{64}$")) throw invalid("previewFingerprint");
        try {
            return store.merge(new TagDefinitionStore.MergeChange(sourceCode, command.targetCode(),
                    command.expectedSourceVersion(), command.expectedTargetVersion(), fingerprint,
                    reason(command.reason()), identifier(memberId), OffsetDateTime.now()));
        } catch (TagDefinitionStore.NotFoundException e) { throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND); }
        catch (TagDefinitionStore.ConcurrentUpdateException e) { throw conflict("TAG_DEFINITION_VERSION_CONFLICT", "최신 태그 정의를 조회한 후 다시 병합해 주세요."); }
        catch (TagDefinitionStore.StaleMergePreviewException e) { throw conflict("TAG_DEFINITION_MERGE_CONFLICT", "병합 미리보기가 만료되었습니다. 다시 확인해 주세요."); }
        catch (TagDefinitionStore.InvalidMergeException e) {
            if ("targetCode".equals(e.code())) throw invalid("targetCode");
            throw conflict("TAG_DEFINITION_MERGE_CONFLICT", "Tag definitions cannot be merged in their current state.");
        }
    }

    private TagDefinition change(TagDefinitionStore.Change value) {
        try { return store.update(value); }
        catch (TagDefinitionStore.NotFoundException e) { throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND); }
        catch (TagDefinitionStore.ConcurrentUpdateException e) { throw conflict("TAG_DEFINITION_VERSION_CONFLICT", "최신 태그 정의를 조회한 후 다시 수정해 주세요."); }
        catch (TagDefinitionStore.DuplicateTermException e) { throw conflict("TAG_TERM_ALREADY_EXISTS", "Tag term already exists."); }
        catch (TagDefinitionStore.MergedSourceMutationException e) { throw conflict("TAG_DEFINITION_MERGE_CONFLICT", "병합된 원본 태그는 내용이나 상태를 변경할 수 없습니다."); }
    }
    private <T> T callStore(java.util.function.Supplier<T> supplier) {
        try { return supplier.get(); } catch (TagDefinitionStore.NotFoundException e) { throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND); }
    }
    private ValidatedTag validateCreate(CreateCommand command) {
        if (command == null) throw invalid("request");
        String code = trimmed(command.code()); String type = trimmed(command.type()); validateCode(code);
        if (!TYPES.contains(type) || !code.startsWith(type + "_")) throw invalid("tagDefinition");
        ValidatedTerms terms = validateTerms(command.displayName(), command.aliases());
        return new ValidatedTag(code, type, terms.displayName(), terms.aliases(), terms.normalizedTerms());
    }
    private ValidatedTerms validateTerms(String rawDisplayName, List<String> rawAliases) {
        String displayName = SafeTextPolicy.requireSafe(rawDisplayName, "displayName");
        List<String> aliases = rawAliases == null ? List.of() : rawAliases.stream().map(a -> SafeTextPolicy.requireSafe(a, "aliases")).toList();
        if (displayName.isEmpty() || displayName.length() > TagTermNormalizer.MAX_RAW_LENGTH || aliases.size() > 20 || aliases.stream().anyMatch(a -> a.isEmpty() || a.length() > TagTermNormalizer.MAX_RAW_LENGTH)) throw invalid("tagDefinition");
        String normalizedDisplay = TagTermNormalizer.normalize(displayName);
        List<String> normalizedAliases = aliases.stream().map(TagTermNormalizer::normalize).toList();
        if (normalizedDisplay.length() > TagTermNormalizer.MAX_NORMALIZED_LENGTH) throw invalid("displayName");
        if (normalizedAliases.stream().anyMatch(a -> a.length() > TagTermNormalizer.MAX_NORMALIZED_LENGTH)) throw invalid("aliases");
        List<String> terms = Stream.concat(Stream.of(normalizedDisplay), normalizedAliases.stream()).toList();
        if (terms.stream().anyMatch(String::isBlank) || new HashSet<>(terms).size() != terms.size()) throw invalid("aliases");
        if (terms.stream().anyMatch(this::isForbidden)) throw new BusinessException(HttpStatus.BAD_REQUEST, "TAG_TERM_FORBIDDEN", "Tag term is forbidden.");
        return new ValidatedTerms(displayName, List.copyOf(aliases), terms);
    }
    private void validateCode(String code) { if (code == null || code.length() < 3 || code.length() > 64 || !CODE.matcher(code).matches()) throw invalid("code"); }
    private String reason(String value) { String result = SafeTextPolicy.requireSafe(value, "reason"); if (result.isBlank() || result.length() > 1000) throw invalid("reason"); return result.trim(); }
    private UUID identifier(String value) { try { return UUID.fromString(value); } catch (IllegalArgumentException | NullPointerException e) { throw new BusinessException(ErrorCode.INVALID_IDENTIFIER); } }
    private boolean isForbidden(String value) { String compact = value.replace(" ", ""); return FORBIDDEN_TERMS.stream().anyMatch(compact::contains); }
    private String trimmed(String value) { return value == null ? "" : value.trim(); }
    private BusinessException invalid(String field) { return new BusinessException(ErrorCode.INVALID_FIELD_VALUE, field, "Invalid tag definition value."); }
    private BusinessException conflict(String code, String message) { return new BusinessException(HttpStatus.CONFLICT, code, message); }
    private record ValidatedTag(String code, String type, String displayName, List<String> aliases, List<String> normalizedTerms) { }
    private record ValidatedTerms(String displayName, List<String> aliases, List<String> normalizedTerms) { }
}
