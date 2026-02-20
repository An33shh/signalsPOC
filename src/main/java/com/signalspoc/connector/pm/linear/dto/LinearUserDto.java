package com.signalspoc.connector.pm.linear.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LinearUserDto {

    private String id;
    private String name;
    private String displayName;
    private String email;
    private boolean active;
    private boolean admin;
    private String avatarUrl;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
