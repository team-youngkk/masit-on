package com.masiton.orchestration.application.command;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.orchestration.application.port.in.AdjustRegisteredCategoryUseCase;
import com.masiton.orchestration.application.port.out.AiRegisteredContentStore;

@Service
public class AdjustRegisteredCategoryService implements AdjustRegisteredCategoryUseCase {

    private final AiRegisteredContentStore store;

    public AdjustRegisteredCategoryService(AiRegisteredContentStore store) {
        this.store = store;
    }

    @Override
    @Transactional
    public void adjust(UUID restaurantId, UUID foodCategoryId) {
        store.updateFoodCategory(restaurantId, foodCategoryId);
    }
}
