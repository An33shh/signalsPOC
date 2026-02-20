package com.signalspoc.shared.exception;

import lombok.Getter;

public final class Exceptions {

    private Exceptions() {}

    @Getter
    public static class ConnectorException extends RuntimeException {

        private final String connectorType;

        public ConnectorException(String connectorType, String message) {
            super(message);
            this.connectorType = connectorType;
        }

        public ConnectorException(String connectorType, String message, Throwable cause) {
            super(message, cause);
            this.connectorType = connectorType;
        }
    }

    public static class ResourceNotFoundException extends RuntimeException {

        public ResourceNotFoundException(String message) {
            super(message);
        }

        public ResourceNotFoundException(String resourceType, Long id) {
            super(String.format("%s not found with id: %d", resourceType, id));
        }

        public ResourceNotFoundException(String resourceType, String externalId, String sourceSystem) {
            super(String.format("%s not found with externalId: %s in system: %s", resourceType, externalId, sourceSystem));
        }
    }

    public static class SyncException extends RuntimeException {

        public SyncException(String message) {
            super(message);
        }

        public SyncException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
