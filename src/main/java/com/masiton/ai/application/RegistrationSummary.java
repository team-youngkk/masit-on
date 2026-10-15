package com.masiton.ai.application;

import java.util.List;
import com.masiton.ai.application.port.out.AiRegistrationUnitStore.RegistrationUnitRow;

public record RegistrationSummary(long total, long autoConfirmed, long autoBlocked, long autoRejected,
                                  long manualOverride, long registered, boolean partialRegistration) {
    public static RegistrationSummary from(List<RegistrationUnitRow> units) {
        long registered = units.stream().filter(unit -> unit.manualOverrideType() == null
                && ("AUTO_CONFIRMED".equals(unit.reviewStatus()) || "MANUAL_OVERRIDE".equals(unit.reviewStatus()))
                && unit.registeredRestaurantId() != null && unit.registeredCreatorId() != null
                && unit.registeredVideoId() != null && unit.registeredVisitId() != null).count();
        return new RegistrationSummary(units.size(), count(units, "AUTO_CONFIRMED"), count(units, "AUTO_BLOCKED"),
                count(units, "AUTO_REJECTED"), count(units, "MANUAL_OVERRIDE"), registered,
                registered > 0 && registered < units.size());
    }
    private static long count(List<RegistrationUnitRow> units, String status) {
        return units.stream().filter(unit -> status.equals(unit.reviewStatus())).count();
    }
}
