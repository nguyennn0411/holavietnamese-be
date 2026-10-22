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
    private volatile String runtimeApiKey = null;

    public GeminiApiClient(
            @Value("${app.ai.gemini-api-key:}") String configuredApiKey,
            @Value("${app.ai.default-model:openrouter/free}") String defaultModel,
            ObjectMapper objectMapper
    ) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
        this.configuredApiKey = configuredApiKey != null ? configuredApiKey.trim() : "";
        this.defaultModel = defaultModel != null && !defaultModel.isBlank() ? defaultModel : "openrouter/free";
    }

    public String getEffectiveApiKey() {
        if (runtimeApiKey != null && !runtimeApiKey.isBlank()) {
            return runtimeApiKey.trim();
        }
        if (configuredApiKey != null && !configuredApiKey.isBlank()) {
            return configuredApiKey.trim();
        }
        String envKey = System.getenv("GEMINI_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey.trim();
        }
        String googleKey = System.getenv("GOOGLE_AI_API_KEY");
        if (googleKey != null && !googleKey.isBlank()) {
            return googleKey.trim();
        }
        return "";
    }

    public boolean isApiKeyAvailable() {
        return !getEffectiveApiKey().isBlank();
    }

    public void setRuntimeApiKey(String key) {
        this.runtimeApiKey = (key != null && !key.isBlank()) ? key.trim() : null;
    }

    public boolean testApiKey(String key) {
        if (key == null || key.isBlank()) return false;
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + defaultModel + ":generateContent";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", key.trim());
            Map<String, Object> req = Map.of(
                    "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", "Xin chào"))))
            );
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(req, headers);
            ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            return res.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Gemini API key verification failed: {}", e.getMessage());
            return false;
        }
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
        List<Map<String, Object>> contents = List.of(
                Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))
        );
        return generateMultiTurn(systemInstruction, contents, modelOverride);
    }

    /**
     * Call Gemini GenerateContent endpoint with system instruction and multi-turn contents.
     * Each content map has {"role": "user"|"model", "parts": [{"text": "..."}]}.
     */
    public GeminiResponse generateMultiTurn(
            String systemInstruction,
            List<Map<String, Object>> contents,
            String modelOverride
    ) {
        String model = (modelOverride != null && !modelOverride.isBlank()) ? modelOverride : defaultModel;
        long startTime = System.currentTimeMillis();
        String apiKey = getEffectiveApiKey();

        if (apiKey.isBlank()) {
            log.info("Gemini API key is not configured. Falling back to intelligent rule-based responder.");
            return null;
        }

        try {
            // Google Gemini API URL
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", apiKey);

            Map<String, Object> requestBody = new HashMap<>();

            // 1. Official Google Gemini systemInstruction
            if (systemInstruction != null && !systemInstruction.isBlank()) {
                requestBody.put("systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", systemInstruction))
                ));
            }

            // 2. Multi-turn conversation contents
            requestBody.put("contents", contents);

            // 3. Generation config
            requestBody.put("generationConfig", Map.of(
                    "temperature", 0.75,
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
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("Gemini API rejected model={} with status={} body={}",
                    model, e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Failed to invoke Google Gemini Multi-Turn API with model={}: {}", model, e.getMessage());
        }

        return null;
    }
}
