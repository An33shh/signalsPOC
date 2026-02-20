package com.signalspoc.connector.pm.linear.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LinearProjectDto {

    private String id;
    private String name;
    private String description;
    private String state;
    private String icon;
    private String color;
    private LinearIssueDto.LinearUserRef lead;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime startDate;
    private OffsetDateTime targetDate;
}
