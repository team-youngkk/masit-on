package com.masiton.visit.application.port.out;

import java.util.UUID;
import com.masiton.visit.application.port.in.ManageVisitTagsUseCase.Change;
import com.masiton.visit.application.port.in.ManageVisitTagsUseCase.Result;

public interface VisitTagStore {
    Result list(UUID restaurantId);
    void replace(UUID restaurantId, UUID visitId, Change change, UUID memberId);
}
