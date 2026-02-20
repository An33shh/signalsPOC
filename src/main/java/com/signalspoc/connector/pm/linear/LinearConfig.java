package com.signalspoc.connector.pm.linear;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "connectors.linear")
@Data
public class LinearConfig {

    private boolean enabled = false;
    private String apiUrl = "https://api.linear.app/graphql";
    private String apiKey;
    private int timeoutSeconds = 30;
    private int retryAttempts = 3;

    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isEmpty();
    }
}
