package com.sep490.backend.modules.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OpenAiApiClient {

    private static final String CLOUDFLARE_SIN_EDGE_IP = "104.26.0.1";

    private final RestTemplate restTemplate = new RestTemplate();
    private final RestTemplate edgeFallbackRestTemplate = createEdgeRestTemplate(CLOUDFLARE_SIN_EDGE_IP);
    private final ObjectMapper objectMapper;
    private final String configuredApiKey;
    private final String baseUrl;
    private volatile String runtimeApiKey;
    private volatile boolean preferEdgeFallback = false;

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
        if (envKey != null && !envKey.isBlank()) {
            return envKey.trim();
        }
        String sysProp = System.getProperty("OPENAI_API_KEY");
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp.trim();
        }
        return readDotEnvKey("OPENAI_API_KEY");
    }

    public String getEffectiveBaseUrl(String apiKey) {
        String key = (apiKey != null && !apiKey.isBlank()) ? apiKey : getEffectiveApiKey();
        if (key.startsWith("sk-or-")) {
            return "https://openrouter.ai/api/v1";
        }
        String envBase = System.getenv("OPENAI_BASE_URL");
        if (envBase != null && !envBase.isBlank()) {
            return envBase.trim().replaceAll("/+$", "");
        }
        String dotEnvBase = readDotEnvKey("OPENAI_BASE_URL");
        if (!dotEnvBase.isBlank()) {
            return dotEnvBase.replaceAll("/+$", "");
        }
        return baseUrl;
    }

    private static String readDotEnvKey(String keyName) {
        try {
            java.nio.file.Path[] candidates = new java.nio.file.Path[]{
                    java.nio.file.Paths.get(".env"),
                    java.nio.file.Paths.get(System.getProperty("user.dir", "."), ".env"),
                    java.nio.file.Paths.get("..", ".env")
            };
            for (java.nio.file.Path p : candidates) {
                if (java.nio.file.Files.isRegularFile(p)) {
                    List<String> lines = java.nio.file.Files.readAllLines(p, java.nio.charset.StandardCharsets.UTF_8);
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith(keyName + "=")) {
                            String val = trimmed.substring((keyName + "=").length()).trim();
                            if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                                val = val.substring(1, val.length() - 1);
                            }
                            if (!val.isBlank()) {
                                return val.trim();
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "";
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
            request.put("max_tokens", 50);
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
            request.put("temperature", 0.7);
            request.put("max_tokens", 4096);
            if (getEffectiveBaseUrl(apiKey).contains("openrouter.ai")) {
                request.put("reasoning", Map.of("effort", "low", "exclude", true));
            }

            ResponseEntity<String> response = exchange(apiKey, request);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode messageNode = root.path("choices").path(0).path("message");
            String text = cleanModelContent(messageNode.path("content").asText(""));
            if (text.isBlank()) {
                // Only salvage from reasoning if the model actually emitted a valid final JSON reply inside it;
                // never return raw chain-of-thought ("Here's a thinking process...") as the assistant message.
                text = extractFinalJsonFromReasoning(messageNode.path("reasoning").asText(""));
            }
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

    String cleanModelContent(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String cleaned = raw.replaceAll("(?is)<think>.*?</think>", "").trim();
        if (cleaned.toLowerCase().contains("<think>")) {
            cleaned = cleaned.substring(0, cleaned.toLowerCase().indexOf("<think>")).trim();
        }
        if (cleaned.contains("</think>")) {
            cleaned = cleaned.substring(cleaned.indexOf("</think>") + 8).trim();
        }
        if (isRawThinkingProcess(cleaned)) {
            String embeddedJson = extractFinalJsonFromReasoning(cleaned);
            return embeddedJson != null ? embeddedJson : "";
        }
        return cleaned;
    }

    boolean isRawThinkingProcess(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String trimmed = text.trim();
        return trimmed.matches("(?is)^(#+\\s*)?(here'?s a thinking process|thinking process|thought process|reasoning process|let'?s analyze|1\\.\\s*\\*\\*analyze user input).*");
    }

    String extractFinalJsonFromReasoning(String reasoning) {
        if (reasoning == null || reasoning.isBlank()) {
            return "";
        }
        int lastClose = reasoning.lastIndexOf('}');
        int searchFrom = lastClose;
        while (searchFrom > 0) {
            int open = reasoning.lastIndexOf('{', searchFrom - 1);
            if (open < 0) {
                break;
            }
            String candidate = reasoning.substring(open, lastClose + 1).trim();
            try {
                JsonNode node = objectMapper.readTree(candidate);
                if (node.isObject() && node.hasNonNull("text")) {
                    String candidateText = node.path("text").asText("").trim();
                    if (!candidateText.isBlank()
                            && !candidateText.startsWith("Conversational reply")
                            && !isRawThinkingProcess(candidateText)) {
                        return candidate;
                    }
                }
            } catch (Exception ignored) {
            }
            searchFrom = open;
        }
        return "";
    }

    private ResponseEntity<String> exchange(String apiKey, Map<String, Object> request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        headers.set("HTTP-Referer", "http://localhost:5173");
        headers.set("X-Title", "Hola Vietnamese");
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
        String resolvedBase = getEffectiveBaseUrl(apiKey);
        String url = resolvedBase + "/chat/completions";
        if (preferEdgeFallback && url.contains("openrouter.ai")) {
            return edgeFallbackRestTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        }
        try {
            return restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        } catch (HttpStatusCodeException ex) {
            String body = ex.getResponseBodyAsString();
            if (url.contains("openrouter.ai") && body != null && body.contains("Request path cannot be relayed")) {
                preferEdgeFallback = true;
                log.info("OpenRouter default edge returned relay block; switching to Singapore Cloudflare edge ({})", CLOUDFLARE_SIN_EDGE_IP);
                return edgeFallbackRestTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            }
            throw ex;
        }
    }

    private static RestTemplate createEdgeRestTemplate(String edgeIp) {
        SSLSocketFactory delegate = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocketFactory sniEdgeFactory = new SSLSocketFactory() {
            @Override
            public String[] getDefaultCipherSuites() {
                return delegate.getDefaultCipherSuites();
            }

            @Override
            public String[] getSupportedCipherSuites() {
                return delegate.getSupportedCipherSuites();
            }

            @Override
            public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
                if (s != null && autoClose) {
                    try {
                        s.close();
                    } catch (IOException ignored) {
                    }
                }
                Socket tcp = new Socket();
                tcp.connect(new InetSocketAddress(edgeIp, port), 10000);
                tcp.setSoTimeout(60000);
                SSLSocket ssl = (SSLSocket) delegate.createSocket(tcp, host, port, true);
                SSLParameters params = ssl.getSSLParameters();
                params.setServerNames(List.of(new SNIHostName(host)));
                params.setEndpointIdentificationAlgorithm("HTTPS");
                ssl.setSSLParameters(params);
                return ssl;
            }

            @Override
            public Socket createSocket(String host, int port) throws IOException {
                return createSocket(null, host, port, true);
            }

            @Override
            public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
                return createSocket(null, host, port, true);
            }

            @Override
            public Socket createSocket(InetAddress host, int port) throws IOException {
                return createSocket(null, host.getHostName(), port, true);
            }

            @Override
            public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
                return createSocket(null, address.getHostName(), port, true);
            }
        };

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                if (connection instanceof HttpsURLConnection httpsConnection) {
                    httpsConnection.setSSLSocketFactory(sniEdgeFactory);
                }
            }
        };
        requestFactory.setConnectTimeout(10000);
        requestFactory.setReadTimeout(60000);
        return new RestTemplate(requestFactory);
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
