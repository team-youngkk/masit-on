package com.masiton.ai.presentation;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase;
import com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase.CreateCommand;
import com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase.Result;
import com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase.TagDefinition;

@RestController
@RequestMapping("/api/admin/tag-definitions")
public class AdminTagDefinitionController {
    private final ManageTagDefinitionsUseCase useCase;

    public AdminTagDefinitionController(ManageTagDefinitionsUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public ResponseEntity<Result> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(useCase.listActive());
    }

    @PostMapping
    public ResponseEntity<TagDefinition> create(@RequestBody(required = false) CreateCommand command) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(useCase.create(command));
    }
}
