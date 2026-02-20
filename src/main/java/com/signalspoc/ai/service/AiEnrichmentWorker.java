package com.signalspoc.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.signalspoc.ai.event.AlertEnrichmentEvent;
import com.signalspoc.ai.model.AiActionRecommendation;
import com.signalspoc.domain.repository.SyncAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Asynchronous AI enrichment worker.
 *
 * Listens for AlertEnrichmentEvent (fired after a new alert is saved) and
 * enriches the alert with an Ollama-generated suggestion + action recommendation.
 * Runs on a dedicated thread pool so it never blocks the detection loop.
 */
@Component
@ConditionalOnProperty(name = "ai.ollama.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class AiEnrichmentWorker {

    private final AiSuggestionService aiSuggestionService;
    private final SyncAlertRepository alertRepository;
    private final ObjectMapper objectMapper;

    @EventListener
    @Async("aiEnrichmentExecutor")
    public void onAlertCreated(AlertEnrichmentEvent event) {
        log.debug("Enriching alert {} with AI suggestion", event.alertId());
        try {
            String suggestion = aiSuggestionService.generateAlertSuggestion(
                    event.alertType(), event.severity(), event.pr(), event.task());

            AiActionRecommendation rec = aiSuggestionService.generateActionRecommendation(
                    event.alertType(), event.severity(), event.pr(), event.task());

            String actionJson = objectMapper.writeValueAsString(rec);
            alertRepository.updateAiEnrichment(event.alertId(), suggestion, actionJson);
            log.info("Alert {} enriched — action: {} (confidence: {})",
                    event.alertId(), rec.getActionType(), rec.getConfidence());

        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize action recommendation for alert {}", event.alertId());
        } catch (Exception e) {
            log.warn("AI enrichment failed for alert {}: {}", event.alertId(), e.getMessage());
        }
    }
}
