package com.signalspoc.connector.pm.linear.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LinearCommentDto {

    private String id;
    private String body;
    private LinearIssueDto.LinearUserRef user;
    private String issueId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
