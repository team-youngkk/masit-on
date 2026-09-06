package com.masiton.visit.application;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.visit.application.port.in.ManageVisitTagsUseCase;
import com.masiton.visit.application.port.out.VisitTagStore;

@Service
public class VisitTagService implements ManageVisitTagsUseCase {
    private final VisitTagStore store;

    public VisitTagService(VisitTagStore store) {
        this.store = store;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result list(String restaurantId) {
        return store.list(identifier(restaurantId));
    }

    @Override
    @Transactional
    public void replace(String restaurantId, String visitId, Change change, String memberId) {
        if (change == null || change.expectedVersion() == null || change.expectedVersion().isBlank()
                || change.expectedVersion().length() > 128 || change.tagCodes() == null
                || change.tagCodes().size() > 50 || change.reason() == null
                || change.reason().trim().isEmpty() || change.reason().trim().length() > 1000
                || change.tagCodes().stream().anyMatch(code -> code == null || code.isBlank() || code.length() > 64)
                || new HashSet<>(change.tagCodes()).size() != change.tagCodes().size()) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE);
        }
        store.replace(identifier(restaurantId), identifier(visitId),
                new Change(change.expectedVersion(), List.copyOf(change.tagCodes()), change.reason().trim()),
                identifier(memberId));
    }

    private UUID identifier(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException();
            }
            return parsed;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INVALID_IDENTIFIER);
        }
    }

}
