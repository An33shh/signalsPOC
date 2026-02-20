package com.signalspoc.connector.model;

import com.signalspoc.shared.model.Enums.ConnectorType;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ConnectorComment {

    private String externalId;
    private ConnectorType sourceSystem;
    private String taskExternalId;
    private String authorExternalId;
    private String content;
    private LocalDateTime createdAt;
}
