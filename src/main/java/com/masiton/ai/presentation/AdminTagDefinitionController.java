package com.masiton.ai.presentation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase;
import com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase.CreateCommand;
import com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase.Result;
import com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase.TagDefinition;
import com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase.ManagementResult;
import com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase.HistoryResult;
import com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase.UpdateCommand;
import com.masiton.ai.application.port.in.ManageTagDefinitionsUseCase.StatusCommand;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/admin/tag-definitions")
public class AdminTagDefinitionController {
    private static final Set<String> UPDATE_FIELDS = Set.of("expectedVersion", "displayName", "aliases", "reason");
    private static final Set<String> STATUS_FIELDS = Set.of("expectedVersion", "status", "reason");
    private static final Set<String> MERGE_FIELDS = Set.of("targetCode", "expectedSourceVersion",
            "expectedTargetVersion", "previewFingerprint", "reason");
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

    @GetMapping("/management")
    public ResponseEntity<ManagementResult> management(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(useCase.list(status, page, size));
    }

    @GetMapping("/{code}")
    public ResponseEntity<TagDefinition> get(@PathVariable String code) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(useCase.get(code));
    }

    @PutMapping("/{code}")
    public ResponseEntity<TagDefinition> update(@PathVariable String code,
            @RequestBody(required = false) JsonNode body, Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(useCase.update(code, updateCommand(body), authentication.getName()));
    }

    @PostMapping("/{code}/status")
    public ResponseEntity<TagDefinition> status(@PathVariable String code,
            @RequestBody(required = false) JsonNode body, Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(useCase.changeStatus(code, statusCommand(body), authentication.getName()));
    }

    @GetMapping("/{code}/history")
    public ResponseEntity<HistoryResult> history(@PathVariable String code,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(useCase.history(code, page, size));
    }

    @GetMapping("/{sourceCode}/merge-preview")
    public ResponseEntity<ManageTagDefinitionsUseCase.MergePreview> mergePreview(
            @PathVariable String sourceCode, @RequestParam String targetCode) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(useCase.previewMerge(sourceCode, targetCode));
    }

    @PostMapping("/{sourceCode}/merge")
    public ResponseEntity<ManageTagDefinitionsUseCase.MergeResult> merge(
            @PathVariable String sourceCode, @RequestBody(required = false) JsonNode body,
            Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(useCase.merge(sourceCode, mergeCommand(body), authentication.getName()));
    }

    private UpdateCommand updateCommand(JsonNode body) {
        requireObject(body, UPDATE_FIELDS);
        return new UpdateCommand(longValue(body.get("expectedVersion")), text(body.get("displayName")),
                textList(body.get("aliases")), text(body.get("reason")));
    }

    private StatusCommand statusCommand(JsonNode body) {
        requireObject(body, STATUS_FIELDS);
        return new StatusCommand(longValue(body.get("expectedVersion")), text(body.get("status")),
                text(body.get("reason")));
    }

    private ManageTagDefinitionsUseCase.MergeCommand mergeCommand(JsonNode body) {
        requireObject(body, MERGE_FIELDS);
        return new ManageTagDefinitionsUseCase.MergeCommand(text(body.get("targetCode")),
                longValue(body.get("expectedSourceVersion")), longValue(body.get("expectedTargetVersion")),
                text(body.get("previewFingerprint")), text(body.get("reason")));
    }

    private void requireObject(JsonNode body, Set<String> fields) {
        if (body == null || !body.isObject()
                || body.propertyNames().stream().anyMatch(name -> !fields.contains(name))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private Long longValue(JsonNode node) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return node.longValue();
    }

    private String text(JsonNode node) {
        if (node == null || !node.isTextual()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return node.asText();
    }

    private List<String> textList(JsonNode node) {
        if (node == null || !node.isArray()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        List<String> values = new ArrayList<>();
        for (JsonNode value : node) {
            if (!value.isTextual()) throw new BusinessException(ErrorCode.INVALID_REQUEST);
            values.add(value.asText());
        }
        return values;
    }
}
