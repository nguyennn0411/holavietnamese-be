package com.sep490.backend.modules.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sep490.backend.modules.ai.client.GeminiApiClient;
import com.sep490.backend.modules.ai.client.OpenAiApiClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Isolated unit tests for the AI Provider router following the f9fac16 pattern.
 */
class AiProviderServiceTests {

    private GeminiApiClient geminiApiClient;
    private OpenAiApiClient openAiApiClient;
    private AiProviderService providerService;

    @BeforeEach
    void setUp() {
        geminiApiClient = mock(GeminiApiClient.class);
        openAiApiClient = mock(OpenAiApiClient.class);
        providerService = new AiProviderService(geminiApiClient, openAiApiClient);
    }

    @Test
    void isOpenAiModelIdentifiesOpenAiCompatibleModelPrefixes() {
        // OpenAI and OpenRouter/Claude/LLaMA routed models
        assertThat(providerService.isOpenAiModel("gpt-4o")).isTrue();
        assertThat(providerService.isOpenAiModel("gpt-3.5-turbo")).isTrue();
        assertThat(providerService.isOpenAiModel("openrouter/free")).isTrue();
        assertThat(providerService.isOpenAiModel("anthropic/claude-3-opus")).isTrue();
        assertThat(providerService.isOpenAiModel("meta-llama/llama-3-70b")).isTrue();

        // Gemini models or unsupported/null
        assertThat(providerService.isOpenAiModel("gemini-2.0-flash")).isFalse();
        assertThat(providerService.isOpenAiModel("gemini-1.5-pro")).isFalse();
        assertThat(providerService.isOpenAiModel(null)).isFalse();
        assertThat(providerService.isOpenAiModel("custom-model")).isFalse();
    }

    @Test
    void generateMultiTurnRoutesToOpenAiClientWhenModelIsOpenAiCompatible() {
        var dummyResponse = new GeminiApiClient.GeminiResponse("OpenAI Answer", 40, 60, 200L, true);
        when(openAiApiClient.generateMultiTurn(anyString(), anyList(), eq("gpt-4o")))
                .thenReturn(dummyResponse);

        var contents = List.of(Map.<String, Object>of("role", "user", "text", "Xin chào"));
        var result = providerService.generateMultiTurn("System prompt", contents, "gpt-4o");

        assertThat(result).isNotNull();
        assertThat(result.text()).isEqualTo("OpenAI Answer");
        verify(openAiApiClient).generateMultiTurn("System prompt", contents, "gpt-4o");
        verifyNoInteractions(geminiApiClient);
    }

    @Test
    void generateMultiTurnRoutesToGeminiClientWhenModelIsGemini() {
        var dummyResponse = new GeminiApiClient.GeminiResponse("Gemini Answer", 50, 70, 310L, true);
        when(geminiApiClient.generateMultiTurn(anyString(), anyList(), eq("gemini-2.0-flash")))
                .thenReturn(dummyResponse);

        var contents = List.of(Map.<String, Object>of("role", "user", "text", "Xin chào"));
        var result = providerService.generateMultiTurn("System prompt", contents, "gemini-2.0-flash");

        assertThat(result).isNotNull();
        assertThat(result.text()).isEqualTo("Gemini Answer");
        verify(geminiApiClient).generateMultiTurn("System prompt", contents, "gemini-2.0-flash");
        verifyNoInteractions(openAiApiClient);
    }

    @Test
    void generateContentWrapsSingleTurnAndDelegatesToConfiguredProvider() {
        var dummyResponse = new GeminiApiClient.GeminiResponse("Single Turn Answer", 20, 30, 150L, true);
        when(geminiApiClient.generateMultiTurn(anyString(), anyList(), eq("gemini-2.0-flash")))
                .thenReturn(dummyResponse);

        var result = providerService.generateContent("System Prompt", "User Prompt", "gemini-2.0-flash");

        assertThat(result.text()).isEqualTo("Single Turn Answer");
        verify(geminiApiClient).generateMultiTurn(
                eq("System Prompt"),
                anyList(),
                eq("gemini-2.0-flash")
        );
    }

    @Test
    void apiKeyMethodsRouteToAppropriateClient() {
        when(openAiApiClient.testApiKey("test-key", "gpt-4o")).thenReturn(true);
        when(geminiApiClient.testApiKey("test-key")).thenReturn(false);

        assertThat(providerService.testApiKey("test-key", "gpt-4o")).isTrue();
        assertThat(providerService.testApiKey("test-key", "gemini-2.0-flash")).isFalse();

        providerService.setRuntimeApiKey("gpt-4o", "sk-openai-key");
        verify(openAiApiClient).setRuntimeApiKey("sk-openai-key");

        providerService.setRuntimeApiKey("gemini-2.0-flash", "AIza-gemini-key");
        verify(geminiApiClient).setRuntimeApiKey("AIza-gemini-key");

        when(openAiApiClient.isApiKeyAvailable()).thenReturn(true);
        when(geminiApiClient.isApiKeyAvailable()).thenReturn(false);
        assertThat(providerService.isApiKeyAvailable("openrouter/free")).isTrue();
        assertThat(providerService.isApiKeyAvailable("gemini-2.0-flash")).isFalse();
    }
}
