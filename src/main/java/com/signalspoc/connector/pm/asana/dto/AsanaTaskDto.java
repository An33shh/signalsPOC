package com.signalspoc.connector.pm.asana.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AsanaTaskDto {

    private String gid;
    private String name;
    private String notes;
    private AsanaUserRef assignee;
    private List<AsanaProjectRef> projects;

    @JsonProperty("due_on")
    private LocalDate dueOn;

    private boolean completed;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("modified_at")
    private LocalDateTime modifiedAt;

    @Data
    public static class AsanaUserRef {
        private String gid;
        private String name;
    }

    @Data
    public static class AsanaProjectRef {
        private String gid;
        private String name;
    }
}
