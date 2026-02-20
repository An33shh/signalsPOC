package com.signalspoc.shared.model;

public final class Enums {

    private Enums() {}

    public enum ConnectorType {
        ASANA,
        LINEAR,
        GITHUB
    }

    public enum Priority {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public enum SyncStatus {
        IN_PROGRESS,
        SUCCESS,
        FAILED
    }
}
