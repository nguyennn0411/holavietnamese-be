package com.sep490.backend.modules.ai.service;
import com.sep490.backend.modules.ai.client.GeminiApiClient;
import com.sep490.backend.modules.ai.client.OpenAiApiClient;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiProviderService {

    private final GeminiApiClient geminiApiClient;
    private final OpenAiApiClient openAiApiClient;

    public GeminiApiClient.GeminiResponse generateMultiTurn(
            String systemInstruction,
            List<Map<String, Object>> contents,
            String model
    ) {
        if (isOpenAiModel(model)) {
            return openAiApiClient.generateMultiTurn(systemInstruction, contents, model);
        }
        return geminiApiClient.generateMultiTurn(systemInstruction, contents, model);
    }

    public GeminiApiClient.GeminiResponse generateContent(
            String systemInstruction,
            String userPrompt,
            String model
    ) {
        return generateMultiTurn(
                systemInstruction,
                List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))),
                model
        );
    }

    public boolean testApiKey(String key, String model) {
        if (isOpenAiModel(model)) {
            return openAiApiClient.testApiKey(key, model);
        }
        return geminiApiClient.testApiKey(key);
    }

    public void setRuntimeApiKey(String model, String key) {
        if (isOpenAiModel(model)) {
            openAiApiClient.setRuntimeApiKey(key);
        } else {
            geminiApiClient.setRuntimeApiKey(key);
        }
    }

    public boolean isApiKeyAvailable(String model) {
        return isOpenAiModel(model) ? openAiApiClient.isApiKeyAvailable() : geminiApiClient.isApiKeyAvailable();
    }

    public String getEffectiveApiKey(String model) {
        return isOpenAiModel(model) ? openAiApiClient.getEffectiveApiKey() : geminiApiClient.getEffectiveApiKey();
    }

    public boolean isOpenAiModel(String model) {
        if (model == null) return false;
        String normalized = model.toLowerCase();
        return normalized.startsWith("gpt-")
                || normalized.startsWith("openrouter/")
                || normalized.startsWith("anthropic/")
                || normalized.startsWith("meta-llama/");
    }
}
