package com.signalspoc.connector.model;

import com.signalspoc.shared.model.Enums.ConnectorType;
import com.signalspoc.shared.model.Enums.Priority;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ConnectorTask {

    private String externalId;
    private ConnectorType sourceSystem;
    private String projectExternalId;
    private String title;
    private String description;
    private String status;
    private Priority priority;
    private String assigneeExternalId;
    private LocalDateTime dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    // GitHub integration fields
    private String externalUrl;
    private String branchName;
    private String linkedPrUrl;
    private String linkedPrStatus;
}
