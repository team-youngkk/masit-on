package com.masiton.security.application;

import java.util.Set;

/**
 * The smallest authenticated-admin context that may cross the security boundary.
 */
public record AdminPrincipal(String adminId, Set<AdminRole> roles) {

    public AdminPrincipal {
        roles = Set.copyOf(roles);
    }

    public boolean hasAdminRole() {
        return roles.contains(AdminRole.ADMIN);
    }
}
