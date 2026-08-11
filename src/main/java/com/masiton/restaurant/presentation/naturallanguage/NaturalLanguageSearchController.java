package com.masiton.restaurant.presentation.naturallanguage;

import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ClientAddressResolver;
import com.masiton.common.web.ErrorCode;
import com.masiton.restaurant.application.naturallanguage.NaturalLanguageSearchService;
import com.masiton.restaurant.application.port.in.NaturalLanguageSearchCommand;

@RestController
@RequestMapping("/api/restaurants")
public class NaturalLanguageSearchController {

    private static final Set<Integer> ALLOWED_SIZES = Set.of(10, 20, 50);

    private final NaturalLanguageSearchService service;
    private final ClientAddressResolver clientAddressResolver;

    public NaturalLanguageSearchController(
            NaturalLanguageSearchService service,
            ClientAddressResolver clientAddressResolver
    ) {
        this.service = service;
        this.clientAddressResolver = clientAddressResolver;
    }

    @PostMapping("/natural-language-search")
    public NaturalLanguageSearchResponse search(
            @RequestBody(required = false) NaturalLanguageSearchRequest request,
            HttpServletRequest httpRequest) {
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "NATURAL_LANGUAGE_EMPTY", "자연어 문장을 입력해 주세요.");
        }
        String sentence = requiredSentence(request.sentence());
        NaturalLanguageSearchRequest.Filters filters = request.filters();
        List<String> tags = normalizeTags(filters == null || filters.tags() == null ? List.of() : filters.tags());
        validateFilters(filters, tags);
        int page = page(request.page());
        int size = size(request.size());

        return NaturalLanguageSearchResponse.from(service.search(new NaturalLanguageSearchCommand(
                sentence,
                normalize(filters == null ? null : filters.query()),
                normalize(filters == null ? null : filters.district()),
                normalize(filters == null ? null : filters.category()),
                normalize(filters == null ? null : filters.creatorId()),
                tags,
                page,
                size,
                clientAddressResolver.resolve(httpRequest))));
    }

    private String requiredSentence(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "NATURAL_LANGUAGE_EMPTY", "자연어 문장을 입력해 주세요.");
        }
        String sentence = raw.trim();
        if (sentence.length() > 500) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "sentence", "최대 500자까지 허용합니다.");
        }
        return sentence;
    }

    private void validateFilters(NaturalLanguageSearchRequest.Filters filters, List<String> tags) {
        if (filters == null) {
            return;
        }
        validateQuery(filters.query());
        for (String tag : tags) {
            if (tag == null || tag.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "filters.tags", "비어 있지 않은 태그만 허용합니다.");
            }
        }
        if (tags.size() > 5) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "filters.tags", "최대 5개까지 허용합니다.");
        }
        if (Set.copyOf(tags).size() != tags.size()) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "filters.tags", "중복 태그는 허용하지 않습니다.");
        }
    }

    private void validateQuery(String raw) {
        if (raw != null && raw.trim().length() > 100) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "filters.query", "최대 100자까지 허용합니다.");
        }
    }

    private List<String> normalizeTags(List<String> rawTags) {
        List<String> normalized = rawTags.stream()
                .map(tag -> {
                    if (tag == null) {
                        throw new BusinessException(
                                ErrorCode.INVALID_FIELD_VALUE, "filters.tags", "비어 있지 않은 태그만 허용합니다.");
                    }
                    return tag.trim();
                })
                .toList();
        return normalized;
    }

    private int page(Integer raw) {
        int value = raw == null ? 1 : raw;
        if (value < 1) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "page", "1 이상의 값만 허용합니다.");
        }
        return value;
    }

    private int size(Integer raw) {
        int value = raw == null ? 20 : raw;
        if (!ALLOWED_SIZES.contains(value)) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "size", "10, 20, 50 중 하나만 허용합니다.");
        }
        return value;
    }

    private String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
