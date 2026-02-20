package com.signalspoc.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ai.ollama")
@Data
public class AiConfig {

    private boolean enabled = false;
    private String url = "http://localhost:11434";
    private String model = "llama3";
    private int timeoutSeconds = 30;
    private int maxTokens = 500;

    // Semantic batch analysis config
    private int analysisMaxTokens = 1500;
    private int analysisBatchSize = 5;
}
