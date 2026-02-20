package com.signalspoc.connector.github;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "connectors.github")
@Data
public class GitHubConfig {

    private boolean enabled = false;
    private String apiUrl = "https://api.github.com";
    private String token;
    private int timeoutSeconds = 30;
    private List<String> repositories;

    public boolean isConfigured() {
        return enabled && token != null && !token.isEmpty();
    }
}
