package com.signalspoc.connector.pm.linear;

import com.signalspoc.connector.pm.linear.dto.*;
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
@ConditionalOnProperty(name = "connectors.linear.enabled", havingValue = "true")
@Slf4j
public class LinearApiClient {

    private final LinearConfig config;
    private final RestTemplate restTemplate;

    public LinearApiClient(LinearConfig config, RestTemplateBuilder restTemplateBuilder) {
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
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", config.getApiKey());
        return headers;
    }

    public boolean testConnection() {
        try {
            String query = "{ viewer { id name email } }";
            var response = executeGraphQL(query, new ParameterizedTypeReference<LinearGraphQLResponse<LinearUserDto>>() {});
            return response != null && response.getData() != null && response.getData().getViewer() != null;
        } catch (Exception e) {
            log.error("Linear connection test failed", e);
            return false;
        }
    }

    public List<LinearUserDto> getAllUsers() {
        String query = """
            query($cursor: String) {
                users(first: 100, after: $cursor) {
                    nodes {
                        id
                        name
                        displayName
                        email
                        active
                        admin
                        createdAt
                        updatedAt
                    }
                    pageInfo {
                        hasNextPage
                        endCursor
                    }
                }
            }
            """;
        return fetchAllPages(query, "users", new ParameterizedTypeReference<>() {});
    }

    public List<LinearProjectDto> getAllProjects() {
        String query = """
            query($cursor: String) {
                projects(first: 100, after: $cursor) {
                    nodes {
                        id
                        name
                        description
                        state
                        createdAt
                        updatedAt
                        startDate
                        targetDate
                        lead {
                            id
                            name
                            email
                        }
                    }
                    pageInfo {
                        hasNextPage
                        endCursor
                    }
                }
            }
            """;
        return fetchAllPages(query, "projects", new ParameterizedTypeReference<>() {});
    }

    public List<LinearIssueDto> getAllIssues() {
        String query = """
            query($cursor: String) {
                issues(first: 100, after: $cursor) {
                    nodes {
                        id
                        identifier
                        title
                        description
                        priority
                        url
                        branchName
                        createdAt
                        updatedAt
                        dueDate
                        state {
                            id
                            name
                            type
                        }
                        assignee {
                            id
                            name
                            email
                        }
                        project {
                            id
                            name
                        }
                        team {
                            id
                            name
                            key
                        }
                    }
                    pageInfo {
                        hasNextPage
                        endCursor
                    }
                }
            }
            """;
        return fetchAllPages(query, "issues", new ParameterizedTypeReference<>() {});
    }

    public List<LinearCommentDto> getCommentsForIssue(String issueId) {
        String query = """
            query($issueId: String!) {
                issue(id: $issueId) {
                    comments(first: 100) {
                        nodes {
                            id
                            body
                            createdAt
                            updatedAt
                            user {
                                id
                                name
                                email
                            }
                        }
                    }
                }
            }
            """;

        try {
            Map<String, Object> variables = Map.of("issueId", issueId);
            var response = executeGraphQL(query, variables, new ParameterizedTypeReference<LinearGraphQLResponse<LinearCommentDto>>() {});

            if (response != null && response.getData() != null && response.getData().getIssue() != null) {
                return new ArrayList<>();
            }
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Error fetching comments for issue: {}", issueId, e);
            return new ArrayList<>();
        }
    }

    public void updateIssueStatus(String issueId, String statusName) {
        String mutation = """
            mutation($issueId: String!, $statusName: String!) {
                issueUpdate(id: $issueId, input: { stateId: $statusName }) {
                    success
                    issue {
                        id
                        state {
                            name
                        }
                    }
                }
            }
            """;

        try {
            Map<String, Object> variables = Map.of("issueId", issueId, "statusName", statusName);
            executeGraphQL(mutation, variables,
                    new ParameterizedTypeReference<LinearGraphQLResponse<Map<String, Object>>>() {});
            log.info("Updated Linear issue {} status to {}", issueId, statusName);
        } catch (Exception e) {
            log.error("Error updating Linear issue {} status", issueId, e);
            throw new ConnectorException("LINEAR", "Failed to update issue status: " + e.getMessage(), e);
        }
    }

    public void addIssueComment(String issueId, String body) {
        String mutation = """
            mutation($issueId: String!, $body: String!) {
                commentCreate(input: { issueId: $issueId, body: $body }) {
                    success
                    comment {
                        id
                    }
                }
            }
            """;

        try {
            Map<String, Object> variables = Map.of("issueId", issueId, "body", body);
            executeGraphQL(mutation, variables,
                    new ParameterizedTypeReference<LinearGraphQLResponse<Map<String, Object>>>() {});
            log.info("Added comment to Linear issue {}", issueId);
        } catch (Exception e) {
            log.error("Error adding comment to Linear issue {}", issueId, e);
            throw new ConnectorException("LINEAR", "Failed to add issue comment: " + e.getMessage(), e);
        }
    }

    public List<LinearCommentDto> getAllComments() {
        String query = """
            query($cursor: String) {
                comments(first: 100, after: $cursor) {
                    nodes {
                        id
                        body
                        createdAt
                        updatedAt
                        user {
                            id
                            name
                            email
                        }
                        issue {
                            id
                        }
                    }
                    pageInfo {
                        hasNextPage
                        endCursor
                    }
                }
            }
            """;
        return fetchAllPages(query, "comments", new ParameterizedTypeReference<>() {});
    }

    private <T> List<T> fetchAllPages(String query, String dataField, ParameterizedTypeReference<LinearGraphQLResponse<T>> typeRef) {
        List<T> allData = new ArrayList<>();
        String cursor = null;

        do {
            try {
                Map<String, Object> variables = cursor != null ? Map.of("cursor", cursor) : Map.of();
                LinearGraphQLResponse<T> response = executeGraphQL(query, variables, typeRef);

                if (response == null || response.getData() == null) {
                    break;
                }

                LinearGraphQLResponse.NodesWrapper<T> wrapper = switch (dataField) {
                    case "users" -> (LinearGraphQLResponse.NodesWrapper<T>) response.getData().getUsers();
                    case "projects" -> (LinearGraphQLResponse.NodesWrapper<T>) response.getData().getProjects();
                    case "issues" -> (LinearGraphQLResponse.NodesWrapper<T>) response.getData().getIssues();
                    case "comments" -> (LinearGraphQLResponse.NodesWrapper<T>) response.getData().getComments();
                    default -> null;
                };

                if (wrapper == null || wrapper.getNodes() == null) {
                    break;
                }

                allData.addAll(wrapper.getNodes());

                if (wrapper.getPageInfo() != null && wrapper.getPageInfo().isHasNextPage()) {
                    cursor = wrapper.getPageInfo().getEndCursor();
                } else {
                    cursor = null;
                }

            } catch (Exception e) {
                log.error("Error fetching {} from Linear", dataField, e);
                throw new ConnectorException("LINEAR", "Failed to fetch " + dataField, e);
            }
        } while (cursor != null);

        return allData;
    }

    private <T> LinearGraphQLResponse<T> executeGraphQL(String query, ParameterizedTypeReference<LinearGraphQLResponse<T>> typeRef) {
        return executeGraphQL(query, Map.of(), typeRef);
    }

    private <T> LinearGraphQLResponse<T> executeGraphQL(String query, Map<String, Object> variables, ParameterizedTypeReference<LinearGraphQLResponse<T>> typeRef) {
        try {
            Map<String, Object> body = Map.of(
                    "query", query,
                    "variables", variables
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, createHeaders());

            ResponseEntity<LinearGraphQLResponse<T>> response = restTemplate.exchange(
                    config.getApiUrl(),
                    HttpMethod.POST,
                    request,
                    typeRef
            );

            LinearGraphQLResponse<T> responseBody = response.getBody();
            if (responseBody != null && responseBody.getErrors() != null && !responseBody.getErrors().isEmpty()) {
                String errorMsg = responseBody.getErrors().get(0).getMessage();
                throw new ConnectorException("LINEAR", "GraphQL error: " + errorMsg);
            }

            return responseBody;
        } catch (RestClientException e) {
            log.error("Error executing Linear GraphQL query", e);
            throw new ConnectorException("LINEAR", "Failed to execute query", e);
        }
    }
}
