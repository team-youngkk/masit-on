package com.masiton.personal.presentation;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.personal.application.port.in.CollectionOption;
import com.masiton.personal.application.port.in.PersonalCollectionUseCase;

@RestController
@RequestMapping("/api/me/collection-options")
public class PersonalCollectionOptionsController {

    private static final String PRIVATE_NO_STORE = "private, no-store";
    private static final Set<String> QUERY_FIELDS = Set.of("restaurantId");

    private final PersonalCollectionUseCase useCase;

    public PersonalCollectionOptionsController(PersonalCollectionUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public ResponseEntity<CollectionOptionsResponse> getCollectionOptions(
            Authentication authentication, @RequestParam MultiValueMap<String, String> query) {
        List<CollectionOption> items = useCase.getCollectionOptions(
                memberId(authentication), restaurantId(query));
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, PRIVATE_NO_STORE)
                .body(new CollectionOptionsResponse(items));
    }

    private UUID restaurantId(MultiValueMap<String, String> query) {
        if (!QUERY_FIELDS.containsAll(query.keySet()) || !query.containsKey("restaurantId")) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        List<String> values = query.get("restaurantId");
        if (values == null || values.size() != 1) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "restaurantId",
                    "값은 한 번만 지정할 수 있습니다.");
        }
        return identifier(values.getFirst());
    }

    private UUID memberId(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
    }

    private UUID identifier(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_IDENTIFIER);
        }
    }

    public record CollectionOptionsResponse(List<CollectionOption> items) {
    }
}
