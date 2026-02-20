package com.signalspoc.connector.model;

import com.signalspoc.shared.model.Enums.ConnectorType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConnectorUser {

    private String externalId;
    private ConnectorType sourceSystem;
    private String name;
    private String email;
    private Boolean isActive;
}
