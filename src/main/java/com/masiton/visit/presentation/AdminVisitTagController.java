package com.masiton.visit.presentation;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.masiton.visit.application.port.in.ManageVisitTagsUseCase;
import com.masiton.visit.application.port.in.ManageVisitTagsUseCase.Change;
import com.masiton.visit.application.port.in.ManageVisitTagsUseCase.Result;

@RestController
@RequestMapping("/api/admin/restaurants/{restaurantId}")
public class AdminVisitTagController {
    private final ManageVisitTagsUseCase useCase;

    public AdminVisitTagController(ManageVisitTagsUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping("/visit-tags")
    public ResponseEntity<Result> list(@PathVariable String restaurantId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(useCase.list(restaurantId));
    }

    @PutMapping("/visits/{visitId}/tags")
    public ResponseEntity<Void> replace(@PathVariable String restaurantId, @PathVariable String visitId,
                                        @RequestBody Change change, Authentication authentication) {
        useCase.replace(restaurantId, visitId, change, authentication.getName());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
