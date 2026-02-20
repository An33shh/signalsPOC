package com.signalspoc.connector.pm.asana;

import com.signalspoc.connector.pm.asana.dto.*;
import com.signalspoc.shared.exception.Exceptions.ConnectorException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "connectors.asana.enabled", havingValue = "true")
@Slf4j
public class AsanaApiClient {

    private final AsanaConfig config;
    private final RestTemplate restTemplate;

    public AsanaApiClient(AsanaConfig config, RestTemplateBuilder restTemplateBuilder) {
        this.config = config;
        this.restTemplate = createRestTemplate(restTemplateBuilder);
    }

    private RestTemplate createRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .setReadTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .additionalInterceptors((request, body, execution) -> {
                    request.getHeaders().set("Authorization", "Bearer " + config.getApiKey());
                    request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    return execution.execute(request, body);
                })
                .build();
    }

    public boolean testConnection() {
        try {
            String url = config.getApiUrl() + "/users/me";
            ResponseEntity<AsanaResponse<AsanaUserDto>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Asana connection test failed", e);
            return false;
        }
    }

    public List<AsanaWorkspaceDto> getWorkspaces() {
        String url = config.getApiUrl() + "/workspaces?limit=100";
        return fetchAllPages(url, new ParameterizedTypeReference<>() {});
    }

    public List<AsanaProjectDto> getProjects(String workspaceGid) {
        String url = config.getApiUrl() + "/workspaces/" + workspaceGid + "/projects?opt_fields=name,owner,created_at,modified_at,current_status&limit=100";
        return fetchAllPages(url, new ParameterizedTypeReference<>() {});
    }

    public List<AsanaProjectDto> getAllProjects() {
        List<AsanaProjectDto> allProjects = new ArrayList<>();
        List<AsanaWorkspaceDto> workspaces = getWorkspaces();

        for (AsanaWorkspaceDto workspace : workspaces) {
            List<AsanaProjectDto> projects = getProjects(workspace.getGid());
            allProjects.addAll(projects);
        }

        return allProjects;
    }

    public AsanaProjectDto getProject(String projectGid) {
        String url = config.getApiUrl() + "/projects/" + projectGid + "?opt_fields=name,owner,created_at,modified_at,current_status";
        return fetchSingle(url, new ParameterizedTypeReference<>() {});
    }

    public List<AsanaTaskDto> getTasksForProject(String projectGid) {
        String url = config.getApiUrl() + "/projects/" + projectGid + "/tasks?opt_fields=name,notes,assignee,due_on,completed,created_at,modified_at&limit=100";
        return fetchAllPages(url, new ParameterizedTypeReference<>() {});
    }

    public AsanaTaskDto getTask(String taskGid) {
        String url = config.getApiUrl() + "/tasks/" + taskGid + "?opt_fields=name,notes,assignee,projects,due_on,completed,created_at,modified_at";
        return fetchSingle(url, new ParameterizedTypeReference<>() {});
    }

    public AsanaUserDto getUser(String userGid) {
        String url = config.getApiUrl() + "/users/" + userGid + "?opt_fields=name,email";
        return fetchSingle(url, new ParameterizedTypeReference<>() {});
    }

    public List<AsanaUserDto> getUsers(String workspaceGid) {
        String url = config.getApiUrl() + "/users?workspace=" + workspaceGid + "&opt_fields=name,email&limit=100";
        return fetchAllPages(url, new ParameterizedTypeReference<>() {});
    }

    public List<AsanaUserDto> getAllUsers() {
        List<AsanaUserDto> allUsers = new ArrayList<>();
        List<AsanaWorkspaceDto> workspaces = getWorkspaces();

        for (AsanaWorkspaceDto workspace : workspaces) {
            List<AsanaUserDto> users = getUsers(workspace.getGid());
            allUsers.addAll(users);
        }

        return allUsers;
    }

    public List<AsanaStoryDto> getStoriesForTask(String taskGid) {
        String url = config.getApiUrl() + "/tasks/" + taskGid + "/stories?opt_fields=text,created_by,created_at,type&limit=100";
        return fetchAllPages(url, new ParameterizedTypeReference<>() {});
    }

    public void updateTask(String taskGid, Map<String, Object> fields) {
        String url = config.getApiUrl() + "/tasks/" + taskGid;
        Map<String, Object> body = Map.of("data", fields);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body);
        try {
            restTemplate.exchange(url, HttpMethod.PUT, entity, new ParameterizedTypeReference<AsanaResponse<AsanaTaskDto>>() {});
            log.info("Updated Asana task {}", taskGid);
        } catch (RestClientException e) {
            log.error("Error updating Asana task {}", taskGid, e);
            throw new ConnectorException("ASANA", "Failed to update task: " + e.getMessage(), e);
        }
    }

    public void addTaskComment(String taskGid, String text) {
        String url = config.getApiUrl() + "/tasks/" + taskGid + "/stories";
        Map<String, Object> body = Map.of("data", Map.of("text", text));
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body);
        try {
            restTemplate.exchange(url, HttpMethod.POST, entity, new ParameterizedTypeReference<AsanaResponse<AsanaStoryDto>>() {});
            log.info("Added comment to Asana task {}", taskGid);
        } catch (RestClientException e) {
            log.error("Error adding comment to Asana task {}", taskGid, e);
            throw new ConnectorException("ASANA", "Failed to add comment: " + e.getMessage(), e);
        }
    }

    public void completeTask(String taskGid) {
        updateTask(taskGid, Map.of("completed", true));
        log.info("Marked Asana task {} as complete", taskGid);
    }

    private <T> List<T> fetchAllPages(String url, ParameterizedTypeReference<AsanaResponse<List<T>>> typeRef) {
        List<T> allData = new ArrayList<>();
        String nextUrl = url;
        String baseDomain = config.getApiUrl().replaceAll("/api/1\\.0$", "");

        while (nextUrl != null) {
            try {
                ResponseEntity<AsanaResponse<List<T>>> response = restTemplate.exchange(
                        nextUrl,
                        HttpMethod.GET,
                        null,
                        typeRef
                );

                AsanaResponse<List<T>> body = response.getBody();
                if (body != null && body.getData() != null) {
                    allData.addAll(body.getData());

                    if (body.getNextPage() != null && body.getNextPage().getUri() != null) {
                        String uri = body.getNextPage().getUri();
                        nextUrl = uri.startsWith("http") ? uri : baseDomain + uri;
                    } else {
                        nextUrl = null;
                    }
                } else {
                    nextUrl = null;
                }

            } catch (RestClientException e) {
                log.error("Error fetching from Asana API: {}", url, e);
                throw new ConnectorException("ASANA", "Failed to fetch data: " + e.getMessage(), e);
            }
        }

        return allData;
    }

    private <T> T fetchSingle(String url, ParameterizedTypeReference<AsanaResponse<T>> typeRef) {
        try {
            ResponseEntity<AsanaResponse<T>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    typeRef
            );

            AsanaResponse<T> body = response.getBody();
            return body != null ? body.getData() : null;

        } catch (RestClientException e) {
            log.error("Error fetching from Asana API: {}", url, e);
            throw new ConnectorException("ASANA", "Failed to fetch data: " + e.getMessage(), e);
        }
    }
}
