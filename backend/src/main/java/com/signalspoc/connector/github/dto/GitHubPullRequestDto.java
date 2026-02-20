package com.signalspoc.connector.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubPullRequestDto {

    private Long id;
    private Integer number;
    private String title;
    private String body;
    private String state;

    @JsonProperty("html_url")
    private String htmlUrl;

    @JsonProperty("diff_url")
    private String diffUrl;

    private Boolean draft;
    private Boolean merged;

    @JsonProperty("merged_at")
    private OffsetDateTime mergedAt;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @JsonProperty("updated_at")
    private OffsetDateTime updatedAt;

    @JsonProperty("closed_at")
    private OffsetDateTime closedAt;

    private GitHubUserDto user;
    private GitHubUserDto assignee;
    private List<GitHubUserDto> assignees;
    private List<GitHubLabelDto> labels;

    private GitHubBranchRef head;
    private GitHubBranchRef base;

    @JsonProperty("mergeable_state")
    private String mergeableState;

    @JsonProperty("linked_issues")
    private List<String> linkedIssues;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GitHubBranchRef {
        private String ref;
        private String sha;
        private String label;
        private GitHubRepoRef repo;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GitHubRepoRef {
        private Long id;
        private String name;

        @JsonProperty("full_name")
        private String fullName;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GitHubLabelDto {
        private Long id;
        private String name;
        private String color;
        private String description;
    }
}
