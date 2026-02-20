package com.signalspoc.connector.model;

import com.signalspoc.shared.model.Enums.ConnectorType;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ConnectorProject {

    private String externalId;
    private ConnectorType sourceSystem;
    private String name;
    private String description;
    private String status;
    private String ownerExternalId;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
}
