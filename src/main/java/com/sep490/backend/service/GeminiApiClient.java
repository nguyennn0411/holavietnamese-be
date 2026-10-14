package com.sep490.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@Slf4j
public class GeminiApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String configuredApiKey;
    private final String defaultModel;

    public GeminiApiClient(
            @Value("${app.ai.gemini-api-key:}") String configuredApiKey,
            @Value("${app.ai.default-model:gemini-1.5-flash}") String defaultModel,
            ObjectMapper objectMapper
    ) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
        this.configuredApiKey = configuredApiKey != null ? configuredApiKey.trim() : "";
        this.defaultModel = defaultModel != null && !defaultModel.isBlank() ? defaultModel : "gemini-1.5-flash";
    }

    public boolean isApiKeyAvailable() {
        return configuredApiKey != null && !configuredApiKey.isBlank();
    }

    public record GeminiResponse(
            String text,
            int tokensIn,
            int tokensOut,
            long latencyMs,
            boolean isLiveCall
    ) {}

    /**
     * Call Gemini GenerateContent endpoint with system instruction and user prompt.
     */
    public GeminiResponse generateContent(String systemInstruction, String userPrompt, String modelOverride) {
        String model = (modelOverride != null && !modelOverride.isBlank()) ? modelOverride : defaultModel;
        long startTime = System.currentTimeMillis();

        if (!isApiKeyAvailable()) {
            log.info("Gemini API key is not configured. Falling back to offline rule-based responder.");
            return null;
        }

        try {
            // Google Gemini API URL
            // Supports models like gemini-1.5-flash, gemini-1.5-pro
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + configuredApiKey;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();

            // System instruction
            if (systemInstruction != null && !systemInstruction.isBlank()) {
                requestBody.put("systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", systemInstruction))
                ));
            }

            // Contents
            requestBody.put("contents", List.of(
                    Map.of(
                            "role", "user",
                            "parts", List.of(Map.of("text", userPrompt))
                    )
            ));

            // Generation config
            requestBody.put("generationConfig", Map.of(
                    "temperature", 0.7,
                    "maxOutputTokens", 1024
            ));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            long latencyMs = System.currentTimeMillis() - startTime;

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode candidates = root.path("candidates");
                if (candidates.isArray() && !candidates.isEmpty()) {
                    JsonNode first = candidates.get(0);
                    JsonNode parts = first.path("content").path("parts");
                    if (parts.isArray() && !parts.isEmpty()) {
                        String reply = parts.get(0).path("text").asText();
                        int promptTokens = root.path("usageMetadata").path("promptTokenCount").asInt(100);
                        int candidatesTokens = root.path("usageMetadata").path("candidatesTokenCount").asInt(150);

                        return new GeminiResponse(reply, promptTokens, candidatesTokens, latencyMs, true);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to invoke Google Gemini API: {}", e.getMessage());
        }

        return null;
    }
}
