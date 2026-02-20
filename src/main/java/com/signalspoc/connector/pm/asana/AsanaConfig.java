package com.signalspoc.connector.pm.asana;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "connectors.asana")
@Validated
@Data
public class AsanaConfig {

    private boolean enabled;

    @NotBlank(message = "Asana API URL is required")
    private String apiUrl;

    private String apiKey;

    private int timeoutSeconds = 30;

    private int retryAttempts = 3;

    private RateLimitConfig rateLimit;

    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    @Data
    public static class RateLimitConfig {
        private int requestsPerMinute = 150;
    }
}
