package com.masiton.restaurant.presentation.rest;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.restaurant.application.port.in.GetRegionHierarchyUseCase;
import com.masiton.restaurant.application.port.in.RegionHierarchyView;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class RegionHierarchyController {

    private final GetRegionHierarchyUseCase getRegionHierarchyUseCase;

    public RegionHierarchyController(GetRegionHierarchyUseCase getRegionHierarchyUseCase) {
        this.getRegionHierarchyUseCase = getRegionHierarchyUseCase;
    }

    @GetMapping("/api/regions")
    public Response getHierarchy(HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return new Response(getRegionHierarchyUseCase.getHierarchy());
    }

    public record Response(List<RegionHierarchyView> items) {
    }
}
