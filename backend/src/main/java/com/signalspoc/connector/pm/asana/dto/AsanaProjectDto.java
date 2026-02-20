package com.signalspoc.connector.pm.asana.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AsanaProjectDto {

    private String gid;
    private String name;
    private AsanaUserRef owner;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("modified_at")
    private LocalDateTime modifiedAt;

    @JsonProperty("current_status")
    private CurrentStatus currentStatus;

    @Data
    public static class AsanaUserRef {
        private String gid;
        private String name;
    }

    @Data
    public static class CurrentStatus {
        private String color;
        private String text;
    }
}
