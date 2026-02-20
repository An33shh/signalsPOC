package com.signalspoc.connector.pm.asana.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AsanaStoryDto {

    private String gid;
    private String text;
    private String type;

    @JsonProperty("created_by")
    private AsanaUserRef createdBy;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @Data
    public static class AsanaUserRef {
        private String gid;
        private String name;
    }
}
