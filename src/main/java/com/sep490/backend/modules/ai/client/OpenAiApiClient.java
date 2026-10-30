package com.sep490.backend.modules.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OpenAiApiClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;
    private final String configuredApiKey;
    private final String baseUrl;
    private volatile String runtimeApiKey;

    public OpenAiApiClient(
            @Value("${app.ai.openai-api-key:}") String configuredApiKey,
            @Value("${app.ai.openai-base-url:https://api.openai.com/v1}") String baseUrl,
            ObjectMapper objectMapper
    ) {
        this.configuredApiKey = configuredApiKey != null ? configuredApiKey.trim() : "";
        this.baseUrl = baseUrl != null && !baseUrl.isBlank()
                ? baseUrl.trim().replaceAll("/+$", "") : "https://api.openai.com/v1";
        this.objectMapper = objectMapper;
    }

    public String getEffectiveApiKey() {
        if (runtimeApiKey != null && !runtimeApiKey.isBlank()) {
            return runtimeApiKey.trim();
        }
        if (!configuredApiKey.isBlank()) {
            return configuredApiKey;
        }
        String envKey = System.getenv("OPENAI_API_KEY");
        return envKey != null ? envKey.trim() : "";
    }

    public boolean isApiKeyAvailable() {
        return !getEffectiveApiKey().isBlank();
    }

    public void setRuntimeApiKey(String key) {
        runtimeApiKey = key != null && !key.isBlank() ? key.trim() : null;
    }

    public boolean testApiKey(String key, String model) {
        if (key == null || key.isBlank()) {
            return false;
        }
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("model", model);
            request.put("messages", List.of(Map.of("role", "user", "content", "Xin chào")));
            request.put("max_tokens", 8);
            ResponseEntity<String> response = exchange(key.trim(), request);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("OpenAI API key verification failed: {}", e.getMessage());
            return false;
        }
    }

    public GeminiApiClient.GeminiResponse generateMultiTurn(
            String systemInstruction,
            List<Map<String, Object>> contents,
            String model
    ) {
        String apiKey = getEffectiveApiKey();
        if (apiKey.isBlank()) {
            return null;
        }

        long start = System.currentTimeMillis();
        try {
            List<Map<String, String>> messages = new ArrayList<>();
            if (systemInstruction != null && !systemInstruction.isBlank()) {
                messages.add(Map.of("role", "system", "content", systemInstruction));
            }
            for (Map<String, Object> content : contents) {
                String role = "model".equals(content.get("role")) ? "assistant" : "user";
                Object parts = content.get("parts");
                String text = extractText(parts);
                if (!text.isBlank()) {
                    messages.add(Map.of("role", role, "content", text));
                }
            }

            Map<String, Object> request = new HashMap<>();
            request.put("model", model);
            request.put("messages", messages);
            request.put("temperature", 0.75);
            request.put("max_tokens", 1024);

            ResponseEntity<String> response = exchange(apiKey, request);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            String text = root.path("choices").path(0).path("message").path("content").asText("");
            if (text.isBlank()) {
                return null;
            }
            int promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
            int completionTokens = root.path("usage").path("completion_tokens").asInt(0);
            return new GeminiApiClient.GeminiResponse(
                    text, promptTokens, completionTokens, System.currentTimeMillis() - start, true
            );
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("OpenAI-compatible API rejected model={} at baseUrl={} with status={} body={}",
                    model, baseUrl, e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("Failed to invoke OpenAI-compatible API with model={} at baseUrl={}: {}",
                    model, baseUrl, e.getMessage());
            return null;
        }
    }

    private ResponseEntity<String> exchange(String apiKey, Map<String, Object> request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        return restTemplate.exchange(
                baseUrl + "/chat/completions",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                String.class
        );
    }

    private String extractText(Object parts) {
        if (!(parts instanceof List<?> partList)) {
            return "";
        }
        return partList.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(part -> String.valueOf(part.getOrDefault("text", "")))
                .reduce("", (left, right) -> left + right);
    }
}
