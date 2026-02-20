package com.signalspoc.connector.pm.linear.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LinearIssueDto {

    private String id;
    private String identifier;
    private String title;
    private String description;
    private Integer priority;
    private LinearStateDto state;
    private LinearUserRef assignee;
    private LinearProjectRef project;
    private LinearTeamDto team;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime dueDate;

    private String url;
    private String branchName;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LinearStateDto {
        private String id;
        private String name;
        private String type;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LinearUserRef {
        private String id;
        private String name;
        private String email;
        private String displayName;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LinearProjectRef {
        private String id;
        private String name;
        private String description;
        private String state;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LinearTeamDto {
        private String id;
        private String name;
        private String key;
    }
}
