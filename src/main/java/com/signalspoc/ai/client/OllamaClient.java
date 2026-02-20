package com.signalspoc.ai.client;

import com.signalspoc.ai.config.AiConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "ai.ollama.enabled", havingValue = "true")
@Slf4j
public class OllamaClient {

    // Injected into every request so the model always has product context,
    // regardless of whether the custom signals-poc Modelfile was used.
    private static final String SYSTEM_PROMPT = """
            You are the AI monitoring engine for Signals POC, a platform that \
            synchronizes GitHub pull requests with Asana and Linear project tasks.

            Alert types: PR_READY_TASK_NOT_UPDATED (PR open but task not in review), \
            PR_MERGED_TASK_OPEN (PR merged but task still open), STALE_PR (7+ days open), \
            STATUS_MISMATCH, ASSIGNEE_MISMATCH, MISSING_LINK.

            Asana statuses: In Progress, In Review, Complete, Blocked. \
            Linear statuses: In Progress, In Review, Done, Cancelled, Backlog.

            For JSON responses, output only valid JSON with no surrounding text or markdown.
            """;

    private final AiConfig config;
    private final RestTemplate restTemplate;

    public OllamaClient(AiConfig config, RestTemplateBuilder restTemplateBuilder) {
        this.config = config;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .setReadTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();
    }

    /** True when using the custom Modelfile — system prompt is already baked into the model weights. */
    private boolean isProductModel() {
        return config.getModel().startsWith("signals-poc");
    }

    public String generateSuggestion(String prompt) {
        try {
            String url = config.getUrl() + "/api/generate";

            Map<String, Object> body = new HashMap<>();
            body.put("model", config.getModel());
            body.put("prompt", prompt);
            if (!isProductModel()) body.put("system", SYSTEM_PROMPT);  // skip when baked into model
            body.put("stream", false);
            body.put("options", Map.of(
                    "num_predict", config.getMaxTokens(),
                    "temperature", 0.4,   // focused but not overly rigid for text output
                    "top_p", 0.9,
                    "repeat_penalty", 1.1
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, Map.class);

            if (response.getBody() != null && response.getBody().containsKey("response")) {
                return (String) response.getBody().get("response");
            }

            return null;
        } catch (Exception e) {
            log.warn("Ollama request failed (is Ollama running?): {}", e.getMessage());
            return null;
        }
    }

    public String generateStructuredResponse(String prompt, int maxTokens) {
        try {
            String url = config.getUrl() + "/api/generate";

            Map<String, Object> body = new HashMap<>();
            body.put("model", config.getModel());
            body.put("prompt", prompt);
            if (!isProductModel()) body.put("system", SYSTEM_PROMPT);  // skip when baked into model
            body.put("stream", false);
            body.put("format", "json");
            body.put("options", Map.of(
                    "num_predict", maxTokens,
                    "temperature", 0.1,   // very deterministic — valid JSON is required
                    "top_p", 0.85,
                    "repeat_penalty", 1.05
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, Map.class);

            if (response.getBody() != null && response.getBody().containsKey("response")) {
                return (String) response.getBody().get("response");
            }

            return null;
        } catch (Exception e) {
            log.warn("Ollama structured request failed: {}", e.getMessage());
            return null;
        }
    }

    public boolean isAvailable() {
        try {
            String url = config.getUrl() + "/api/tags";
            restTemplate.getForEntity(url, Map.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
