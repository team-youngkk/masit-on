package com.masiton.ai.infrastructure.provider.config;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import com.masiton.ai.application.port.out.AiProviderException;
import com.masiton.ai.application.port.out.AiProviderFailureCategory;
import com.masiton.ai.application.port.out.AiVideoExtractionProvider;
import com.masiton.ai.application.port.out.dto.AiVideoExtractionRequest;
import com.masiton.ai.application.port.out.dto.AiVideoExtractionResult;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** HTTP-only Gemini Developer API adapter. It neither persists nor logs prompts or provider bodies. */
final class GeminiHttpVideoExtractionAdapter implements AiVideoExtractionProvider {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final GeminiProviderProperties properties;

    GeminiHttpVideoExtractionAdapter(HttpClient httpClient, ObjectMapper objectMapper, GeminiProviderProperties properties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public AiVideoExtractionResult extract(AiVideoExtractionRequest request) {
        assertFreeTierCallAllowed();
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(requestUri())
                    .timeout(properties.getResponseTimeout())
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", properties.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody(request))))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                throw new AiProviderException(AiProviderFailureCategory.RATE_LIMIT);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiProviderException(AiProviderFailureCategory.UPSTREAM);
            }
            return normalize(response);
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new AiProviderException(AiProviderFailureCategory.TIMEOUT, exception);
        } catch (IOException exception) {
            throw new AiProviderException(AiProviderFailureCategory.UPSTREAM, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderException(AiProviderFailureCategory.TIMEOUT, exception);
        } catch (JacksonException exception) {
            throw new AiProviderException(AiProviderFailureCategory.SCHEMA, exception);
        }
    }

    private void assertFreeTierCallAllowed() {
        if (!properties.isEnabled() || !properties.isFreeTierVerified() || properties.isPaidBillingEnabled()
                || properties.getApiKey().isBlank()) {
            throw new AiProviderException(AiProviderFailureCategory.PROVIDER_BLOCKED);
        }
    }

    private URI requestUri() {
        String baseUrl = properties.getBaseUrl().replaceAll("/+$", "");
        return URI.create(baseUrl + "/v1beta/models/" + properties.getModel() + ":generateContent");
    }

    private ObjectNode requestBody(AiVideoExtractionRequest request) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode contents = root.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", promptFor(request));
        parts.addObject().putObject("fileData").put("fileUri", request.videoUrl().toString());

        ObjectNode generationConfig = root.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.set("responseJsonSchema", extractionSchema());
        return root;
    }

    private String promptFor(AiVideoExtractionRequest request) {
        String supplement = request.supplementText().isBlank() ? "" : "\nSupplement: " + request.supplementText();
        return "P1: Extract restaurant visit candidates only from the supplied public YouTube video. "
                + "Return S1 JSON that matches the supplied schema." + supplement;
    }

    private ObjectNode extractionSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ArrayNode required = schema.putArray("required");
        required.add("resultCompleteness");
        required.add("candidates");
        required.add("missingFields");
        ObjectNode propertiesNode = schema.putObject("properties");
        propertiesNode.putObject("resultCompleteness").put("type", "string").putArray("enum").add("COMPLETE").add("PARTIAL");
        propertiesNode.putObject("candidates").put("type", "array");
        propertiesNode.putObject("missingFields").put("type", "array");
        return schema;
    }

    private AiVideoExtractionResult normalize(HttpResponse<String> response) throws JacksonException {
        JsonNode envelope = objectMapper.readTree(response.body());
        JsonNode text = envelope.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (!text.isTextual()) {
            throw new AiProviderException(AiProviderFailureCategory.SCHEMA);
        }
        JsonNode payload = objectMapper.readTree(text.textValue());
        if (!validS1(payload)) {
            throw new AiProviderException(AiProviderFailureCategory.SCHEMA);
        }
        return new AiVideoExtractionResult(payload, requestId(envelope).orElse(null));
    }

    private boolean validS1(JsonNode payload) {
        if (!payload.isObject() || !payload.path("candidates").isArray() || !payload.path("missingFields").isArray()) {
            return false;
        }
        String completeness = payload.path("resultCompleteness").asText();
        if (!("COMPLETE".equals(completeness) || "PARTIAL".equals(completeness))) {
            return false;
        }
        for (JsonNode missingField : payload.path("missingFields")) {
            if (!missingField.isTextual() || missingField.textValue().isBlank()) {
                return false;
            }
        }
        for (JsonNode candidate : payload.path("candidates")) {
            if (!validCandidate(candidate)) {
                return false;
            }
        }
        return true;
    }

    private boolean validCandidate(JsonNode candidate) {
        if (!candidate.isObject()
                || !candidate.path("field").isTextual()
                || candidate.path("field").textValue().isBlank()
                || !candidate.path("confidence").isNumber()
                || candidate.path("confidence").doubleValue() < 0
                || candidate.path("confidence").doubleValue() > 1
                || !candidate.path("evidence").isObject()) {
            return false;
        }
        if ("tag".equals(candidate.path("field").textValue())) {
            if (!candidate.path("candidateTagId").isTextual()
                    || candidate.path("candidateTagId").textValue().isBlank()
                    || !candidate.path("tagType").isTextual()
                    || !allowedTagType(candidate.path("tagType").textValue())
                    || !candidate.path("rawLabel").isTextual()
                    || candidate.path("rawLabel").textValue().isBlank()
                    || !candidate.path("normalizedCode").isTextual()
                    || candidate.path("normalizedCode").textValue().isBlank()
                    || !candidate.path("label").isTextual()
                    || candidate.path("label").textValue().isBlank()) {
                return false;
            }
        } else if (!candidate.has("value")) {
            return false;
        }
        return validEvidence(candidate.path("evidence"));
    }

    private boolean allowedTagType(String tagType) {
        return switch (tagType) {
            case "MENU", "TASTE", "OCCASION", "ATMOSPHERE" -> true;
            default -> false;
        };
    }

    private boolean validEvidence(JsonNode evidence) {
        String type = evidence.path("type").asText();
        return switch (type) {
            case "TIMESTAMP" -> nonNegativeRange(evidence, "startMs", "endMs");
            case "TEXT_RANGE" -> nonNegativeRange(evidence, "startOffset", "endOffset")
                    && evidence.path("sourceHash").isTextual()
                    && !evidence.path("sourceHash").textValue().isBlank();
            case "UNKNOWN" -> evidence.size() == 1 && evidence.has("type");
            default -> false;
        };
    }

    private boolean nonNegativeRange(JsonNode node, String startField, String endField) {
        return node.path(startField).canConvertToLong()
                && node.path(endField).canConvertToLong()
                && node.path(startField).longValue() >= 0
                && node.path(endField).longValue() >= node.path(startField).longValue();
    }

    private Optional<String> requestId(JsonNode envelope) {
        JsonNode responseId = envelope.path("responseId");
        return responseId.isTextual() && responseId.textValue().length() <= 128
                ? Optional.of(responseId.textValue()) : Optional.empty();
    }
}
