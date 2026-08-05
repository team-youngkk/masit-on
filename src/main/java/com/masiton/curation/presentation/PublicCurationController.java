package com.masiton.curation.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.curation.application.port.in.PublicCurationUseCase;
import com.masiton.curation.application.port.in.PublicCurationUseCase.PublicCuration;

@RestController
@RequestMapping("/api/curations")
public class PublicCurationController {

    private final PublicCurationUseCase useCase;

    public PublicCurationController(PublicCurationUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public PublicCurationListResponse getCurations() {
        return new PublicCurationListResponse(useCase.getPublishedCurations());
    }

    @GetMapping("/{curationId}")
    public PublicCuration getCuration(@PathVariable String curationId) {
        return useCase.getPublishedCuration(identifier(curationId));
    }

    private UUID identifier(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_IDENTIFIER);
        }
    }

    public record PublicCurationListResponse(List<PublicCuration> items) { }
}
