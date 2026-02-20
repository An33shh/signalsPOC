package com.signalspoc.connector.github;

import com.signalspoc.connector.github.dto.GitHubPullRequestDto;
import com.signalspoc.connector.github.dto.GitHubRepositoryDto;
import com.signalspoc.connector.github.dto.GitHubUserDto;
import com.signalspoc.shared.exception.Exceptions.ConnectorException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "connectors.github.enabled", havingValue = "true")
@Slf4j
public class GitHubApiClient {

    private final GitHubConfig config;
    private final RestTemplate restTemplate;

    public GitHubApiClient(GitHubConfig config, RestTemplateBuilder restTemplateBuilder) {
        this.config = config;
        this.restTemplate = createRestTemplate(restTemplateBuilder);
    }

    private RestTemplate createRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .setReadTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("Authorization", "Bearer " + config.getToken());
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        return headers;
    }

    public boolean testConnection() {
        try {
            String url = config.getApiUrl() + "/user";
            HttpEntity<Void> entity = new HttpEntity<>(createHeaders());

            ResponseEntity<GitHubUserDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    GitHubUserDto.class
            );
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("GitHub connection test failed", e);
            return false;
        }
    }

    public GitHubUserDto getCurrentUser() {
        try {
            String url = config.getApiUrl() + "/user";
            HttpEntity<Void> entity = new HttpEntity<>(createHeaders());

            ResponseEntity<GitHubUserDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    GitHubUserDto.class
            );
            return response.getBody();
        } catch (RestClientException e) {
            log.error("Error fetching current user", e);
            throw new ConnectorException("GITHUB", "Failed to fetch current user", e);
        }
    }

    public List<GitHubPullRequestDto> getPullRequests(String owner, String repo, String state) {
        List<GitHubPullRequestDto> allPRs = new ArrayList<>();
        int page = 1;
        int perPage = 100;

        while (true) {
            try {
                String url = String.format("%s/repos/%s/%s/pulls?state=%s&page=%d&per_page=%d",
                        config.getApiUrl(), owner, repo, state, page, perPage);

                HttpEntity<Void> entity = new HttpEntity<>(createHeaders());

                ResponseEntity<List<GitHubPullRequestDto>> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        entity,
                        new ParameterizedTypeReference<>() {}
                );

                List<GitHubPullRequestDto> prs = response.getBody();
                if (prs == null || prs.isEmpty()) {
                    break;
                }

                allPRs.addAll(prs);

                if (prs.size() < perPage) {
                    break;
                }
                page++;

            } catch (RestClientException e) {
                log.error("Error fetching PRs for {}/{}", owner, repo, e);
                throw new ConnectorException("GITHUB", "Failed to fetch pull requests", e);
            }
        }

        return allPRs;
    }

    public List<GitHubPullRequestDto> getAllOpenPullRequests() {
        List<GitHubPullRequestDto> allPRs = new ArrayList<>();

        if (config.getRepositories() == null) {
            return allPRs;
        }

        for (String repoFullName : config.getRepositories()) {
            String[] parts = repoFullName.split("/");
            if (parts.length == 2) {
                List<GitHubPullRequestDto> prs = getPullRequests(parts[0], parts[1], "open");
                allPRs.addAll(prs);
            }
        }

        return allPRs;
    }

    public GitHubPullRequestDto getPullRequest(String owner, String repo, int prNumber) {
        try {
            String url = String.format("%s/repos/%s/%s/pulls/%d",
                    config.getApiUrl(), owner, repo, prNumber);

            HttpEntity<Void> entity = new HttpEntity<>(createHeaders());

            ResponseEntity<GitHubPullRequestDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    GitHubPullRequestDto.class
            );

            return response.getBody();
        } catch (RestClientException e) {
            log.error("Error fetching PR #{} for {}/{}", prNumber, owner, repo, e);
            throw new ConnectorException("GITHUB", "Failed to fetch pull request", e);
        }
    }

    public GitHubRepositoryDto getRepository(String owner, String repo) {
        try {
            String url = String.format("%s/repos/%s/%s", config.getApiUrl(), owner, repo);

            HttpEntity<Void> entity = new HttpEntity<>(createHeaders());

            ResponseEntity<GitHubRepositoryDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    GitHubRepositoryDto.class
            );

            return response.getBody();
        } catch (RestClientException e) {
            log.error("Error fetching repository {}/{}", owner, repo, e);
            throw new ConnectorException("GITHUB", "Failed to fetch repository", e);
        }
    }

    public void addPRComment(String owner, String repo, int prNumber, String body) {
        try {
            String url = String.format("%s/repos/%s/%s/issues/%d/comments",
                    config.getApiUrl(), owner, repo, prNumber);

            Map<String, String> requestBody = Map.of("body", body);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, createHeaders());

            restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            log.info("Added comment to PR #{} in {}/{}", prNumber, owner, repo);
        } catch (RestClientException e) {
            log.error("Error adding comment to PR #{} in {}/{}", prNumber, owner, repo, e);
            throw new ConnectorException("GITHUB", "Failed to add PR comment", e);
        }
    }

    public void approvePR(String owner, String repo, int prNumber, String body) {
        try {
            String url = String.format("%s/repos/%s/%s/pulls/%d/reviews",
                    config.getApiUrl(), owner, repo, prNumber);

            Map<String, String> requestBody = Map.of(
                    "event", "APPROVE",
                    "body", body != null ? body : "");
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, createHeaders());

            restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            log.info("Approved PR #{} in {}/{}", prNumber, owner, repo);
        } catch (RestClientException e) {
            log.error("Error approving PR #{} in {}/{}", prNumber, owner, repo, e);
            throw new ConnectorException("GITHUB", "Failed to approve pull request", e);
        }
    }

    public void updatePRLabels(String owner, String repo, int prNumber, List<String> labels) {
        try {
            String url = String.format("%s/repos/%s/%s/issues/%d/labels",
                    config.getApiUrl(), owner, repo, prNumber);

            Map<String, List<String>> requestBody = Map.of("labels", labels);
            HttpEntity<Map<String, List<String>>> entity = new HttpEntity<>(requestBody, createHeaders());

            restTemplate.exchange(url, HttpMethod.PUT, entity, List.class);
            log.info("Updated labels on PR #{} in {}/{}", prNumber, owner, repo);
        } catch (RestClientException e) {
            log.error("Error updating labels on PR #{} in {}/{}", prNumber, owner, repo, e);
            throw new ConnectorException("GITHUB", "Failed to update PR labels", e);
        }
    }

    public List<GitHubUserDto> getCollaborators(String owner, String repo) {
        List<GitHubUserDto> allUsers = new ArrayList<>();
        int page = 1;
        int perPage = 100;

        while (true) {
            try {
                String url = String.format("%s/repos/%s/%s/collaborators?page=%d&per_page=%d",
                        config.getApiUrl(), owner, repo, page, perPage);

                HttpEntity<Void> entity = new HttpEntity<>(createHeaders());

                ResponseEntity<List<GitHubUserDto>> response = restTemplate.exchange(
                        url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});

                List<GitHubUserDto> users = response.getBody();
                if (users == null || users.isEmpty()) break;

                allUsers.addAll(users);
                if (users.size() < perPage) break;
                page++;
            } catch (RestClientException e) {
                log.error("Error fetching collaborators for {}/{}", owner, repo, e);
                throw new ConnectorException("GITHUB", "Failed to fetch collaborators", e);
            }
        }

        return allUsers;
    }

    public List<Map<String, Object>> getPRComments(String owner, String repo, int prNumber) {
        try {
            String url = String.format("%s/repos/%s/%s/issues/%d/comments",
                    config.getApiUrl(), owner, repo, prNumber);

            HttpEntity<Void> entity = new HttpEntity<>(createHeaders());

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});

            return response.getBody() != null ? response.getBody() : new ArrayList<>();
        } catch (RestClientException e) {
            log.error("Error fetching comments for PR #{} in {}/{}", prNumber, owner, repo, e);
            return new ArrayList<>();
        }
    }

    public List<String> extractLinkedIssues(GitHubPullRequestDto pr) {
        List<String> linkedIssues = new ArrayList<>();

        String textToSearch = (pr.getTitle() != null ? pr.getTitle() : "") + " " +
                              (pr.getBody() != null ? pr.getBody() : "");

        java.util.regex.Pattern linearPattern = java.util.regex.Pattern.compile(
                "([A-Z]{2,10}-\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = linearPattern.matcher(textToSearch);
        while (matcher.find()) {
            linkedIssues.add(matcher.group(1).toUpperCase());
        }

        return linkedIssues;
    }
}
