package com.masiton.restaurant.application.port.in;

import java.util.List;

public record RegionHierarchyView(String code, String name, List<Child> children) {

    public RegionHierarchyView {
        children = List.copyOf(children);
    }

    public record Child(String code, String name) {
    }
}
