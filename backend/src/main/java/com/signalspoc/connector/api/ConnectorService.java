package com.signalspoc.connector.api;

import com.signalspoc.shared.model.Enums.ConnectorType;

public interface ConnectorService {

    ConnectorType getConnectorType();

    boolean testConnection();
}
