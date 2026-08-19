package com.masiton.common.security;

import java.util.UUID;

/**
 * Resolves the legacy admin-account actor that owns write-path foreign keys.
 */
public interface LegacyAdminActorResolver {

    UUID resolve(UUID memberAccountId);
}
