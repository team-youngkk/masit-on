package com.masiton.personalization.presentation;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.personalization.application.port.in.PersonalRestaurantUseCase;

@RestController
@RequestMapping("/api/me")
public class PersonalRestaurantController {

    private static final Set<String> PAGE_FIELDS = Set.of("page", "size");
    private static final Set<Integer> ALLOWED_SIZES = Set.of(10, 20, 50);
    private static final String PRIVATE_NO_STORE = "private, no-store";

    private final PersonalRestaurantUseCase useCase;

    public PersonalRestaurantController(PersonalRestaurantUseCase useCase) {
        this.useCase = useCase;
    }

    @PutMapping("/favorites/{restaurantId}")
    public ResponseEntity<PersonalRestaurantResponse.FavoriteState> addFavorite(
            Authentication authentication, @PathVariable String restaurantId
    ) {
        UUID id = restaurantId(restaurantId);
        boolean favorited = useCase.addFavorite(memberId(authentication), id);
        return privateResponse(new PersonalRestaurantResponse.FavoriteState(id.toString(), favorited));
    }

    @DeleteMapping("/favorites/{restaurantId}")
    public ResponseEntity<PersonalRestaurantResponse.FavoriteState> removeFavorite(
            Authentication authentication, @PathVariable String restaurantId
    ) {
        UUID id = restaurantId(restaurantId);
        boolean favorited = useCase.removeFavorite(memberId(authentication), id);
        return privateResponse(new PersonalRestaurantResponse.FavoriteState(id.toString(), favorited));
    }

    @GetMapping("/favorites/{restaurantId}")
    public ResponseEntity<PersonalRestaurantResponse.FavoriteState> getFavorite(
            Authentication authentication, @PathVariable String restaurantId
    ) {
        UUID id = restaurantId(restaurantId);
        boolean favorited = useCase.isFavorite(memberId(authentication), id);
        return privateResponse(new PersonalRestaurantResponse.FavoriteState(id.toString(), favorited));
    }

    @GetMapping("/favorites")
    public ResponseEntity<PersonalRestaurantResponse.FavoriteList> getFavorites(
            Authentication authentication,
            @RequestParam MultiValueMap<String, String> query
    ) {
        PageRequest page = page(query);
        return privateResponse(PersonalRestaurantResponse.FavoriteList.from(
                useCase.getFavorites(memberId(authentication), page.number, page.size)));
    }

    @GetMapping("/recent-restaurants")
    public ResponseEntity<PersonalRestaurantResponse.RecentList> getRecentRestaurants(
            Authentication authentication,
            @RequestParam MultiValueMap<String, String> query
    ) {
        PageRequest page = page(query);
        return privateResponse(PersonalRestaurantResponse.RecentList.from(
                useCase.getRecentRestaurants(memberId(authentication), page.number, page.size)));
    }

    @DeleteMapping("/recent-restaurants/{restaurantId}")
    public ResponseEntity<PersonalRestaurantResponse.RecentState> removeRecentRestaurant(
            Authentication authentication, @PathVariable String restaurantId
    ) {
        UUID id = restaurantId(restaurantId);
        boolean recorded = useCase.removeRecentRestaurant(memberId(authentication), id);
        return privateResponse(new PersonalRestaurantResponse.RecentState(id.toString(), recorded));
    }

    private <T> ResponseEntity<T> privateResponse(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, PRIVATE_NO_STORE).body(body);
    }

    private UUID memberId(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "인증이 필요합니다.");
        }
    }

    private UUID restaurantId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "INVALID_IDENTIFIER", "식별자 형식이 올바르지 않습니다.");
        }
    }

    private PageRequest page(MultiValueMap<String, String> query) {
        for (String field : query.keySet()) {
            if (!PAGE_FIELDS.contains(field)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            List<String> values = query.get(field);
            if (values != null && values.size() > 1) {
                throw new BusinessException(
                        ErrorCode.INVALID_FIELD_VALUE, field, "값은 한 번만 지정할 수 있습니다.");
            }
        }
        int number = integer(query.getFirst("page"), "page", 1);
        int size = integer(query.getFirst("size"), "size", 20);
        if (number < 1) {
            throw new BusinessException(
                    ErrorCode.INVALID_FIELD_VALUE, "page", "1 이상의 값만 허용합니다.");
        }
        if (!ALLOWED_SIZES.contains(size)) {
            throw new BusinessException(
                    ErrorCode.INVALID_FIELD_VALUE, "size", "10, 20, 50 중 하나만 허용합니다.");
        }
        return new PageRequest(number, size);
    }

    private int integer(String raw, String field, int defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw new BusinessException(
                    ErrorCode.INVALID_FIELD_VALUE, field, "정수 값만 허용합니다.");
        }
    }

    private record PageRequest(int number, int size) {
    }
}
