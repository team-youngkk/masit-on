package com.masiton.orchestration.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.masiton.orchestration.application.port.in.DecomposeRegistrationUnitsUseCase;

import tools.jackson.databind.JsonNode;

/**
 * S3 uses explicit nested unit binding. Legacy S1/S2 with several restaurants stays unresolved;
 * array positions and values from other units are never used to reconstruct a relation.
 */
@Service
class DecomposeRegistrationUnitsService implements DecomposeRegistrationUnitsUseCase {

    private static final String RESTAURANT_NAME = "restaurantName";
    private static final String ADDRESS = "address";
    private static final String MENU = "menu";
    private static final String LOCATION = "location";
    private static final String VISIT_EVIDENCE = "visitEvidence";

    @Override
    public List<RegistrationUnitBundle> decompose(DecomposeRegistrationUnitsCommand command) {
        Objects.requireNonNull(command, "command");
        JsonNode fields = command.candidateFields();
        JsonNode confidences = command.fieldConfidences();
        JsonNode evidence = command.evidence();

        if (fields != null && fields.path("registrationUnits").isArray()) {
            return decomposeBoundUnits(fields.path("registrationUnits"));
        }
        List<CandidateEntry> nameCandidates = readCandidates(fields, confidences, evidence, RESTAURANT_NAME);
        if (nameCandidates.isEmpty()) {
            return List.of();
        }

        // S2 supplied independent field arrays.  Multiple restaurant entries have no contractual
        // correlation, so do not reconstruct a relationship from their positions.
        if (nameCandidates.size() > 1) {
            return List.of();
        }
        List<RegistrationUnitBundle> bundles = new ArrayList<>(nameCandidates.size());
        for (int index = 0; index < nameCandidates.size(); index++) {
            int unitIndex = index + 1;
            BoundCandidate restaurantName = toBound(nameCandidates.get(index));
            BoundCandidate address = bind(fields, confidences, evidence, ADDRESS, index);
            BoundCandidate menu = bind(fields, confidences, evidence, MENU, index);
            BoundCandidate location = bind(fields, confidences, evidence, LOCATION, index);
            BoundCandidate visitEvidence = bind(fields, confidences, evidence, VISIT_EVIDENCE, index);
            bundles.add(new RegistrationUnitBundle(unitIndex, restaurantName, address, location,
                    menu == null ? List.of() : List.of(menu), visitEvidence));
        }
        return List.copyOf(bundles);
    }

    private List<RegistrationUnitBundle> decomposeBoundUnits(JsonNode units) {
        List<RegistrationUnitBundle> bundles = new ArrayList<>();
        java.util.Set<String> ids = new java.util.HashSet<>();
        int index = 1;
        for (JsonNode unit : units) {
            String id = unit.path("candidateUnitId").asText();
            if (id.isBlank() || !ids.add(id)) return List.of();
            BoundCandidate restaurant = readBound(unit.path(RESTAURANT_NAME));
            if (restaurant == null) return List.of();
            List<BoundCandidate> menus = new ArrayList<>();
            JsonNode menuNodes = unit.path("menus");
            if (menuNodes.isArray()) for (JsonNode menu : menuNodes) {
                BoundCandidate bound = readBound(menu);
                if (bound != null) menus.add(bound);
            }
            bundles.add(new RegistrationUnitBundle(index++, id, restaurant, readBound(unit.path(ADDRESS)),
                    readBound(unit.path(LOCATION)), List.copyOf(menus), readBound(unit.path(VISIT_EVIDENCE))));
        }
        return List.copyOf(bundles);
    }

    private BoundCandidate readBound(JsonNode node) {
        String value = node.path("value").isTextual() ? node.path("value").textValue() : null;
        return value == null || value.isBlank() ? null
                : new BoundCandidate(value, toConfidence(node.path("confidence")), node.path("evidence"));
    }

    private BoundCandidate bind(JsonNode fields, JsonNode confidences, JsonNode evidence, String field, int index) {
        List<CandidateEntry> candidates = readCandidates(fields, confidences, evidence, field);
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return toBound(candidates.get(0));
        }
        return null;
    }

    private List<CandidateEntry> readCandidates(JsonNode fields, JsonNode confidences, JsonNode evidence,
                                                String field) {
        if (fields == null) {
            return List.of();
        }
        JsonNode value = fields.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return List.of();
        }
        if (value.isArray()) {
            List<CandidateEntry> entries = new ArrayList<>();
            for (JsonNode item : value) {
                String itemValue = item.path("value").isTextual() ? item.path("value").textValue() : null;
                if (itemValue == null || itemValue.isBlank()) {
                    continue;
                }
                entries.add(new CandidateEntry(itemValue, toConfidence(item.path("confidence")),
                        item.path("evidence")));
            }
            return List.copyOf(entries);
        }
        if (value.isTextual() && !value.textValue().isBlank()) {
            BigDecimal confidence = confidences == null ? null : toConfidence(confidences.path(field));
            JsonNode fieldEvidence = evidence == null ? null : evidence.path(field);
            return List.of(new CandidateEntry(value.textValue(), confidence, fieldEvidence));
        }
        return List.of();
    }

    private BigDecimal toConfidence(JsonNode node) {
        return node != null && node.isNumber() ? BigDecimal.valueOf(node.doubleValue()) : null;
    }

    private BoundCandidate toBound(CandidateEntry entry) {
        return new BoundCandidate(entry.value(), entry.confidence(), entry.evidence());
    }

    private record CandidateEntry(String value, BigDecimal confidence, JsonNode evidence) {
    }
}
