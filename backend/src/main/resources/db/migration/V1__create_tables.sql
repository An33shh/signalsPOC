-- Users table
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(255) NOT NULL,
    source_system VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(external_id, source_system)
);

-- Projects table
CREATE TABLE IF NOT EXISTS projects (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(255) NOT NULL,
    source_system VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(100),
    owner_id BIGINT REFERENCES users(id),
    external_created_at TIMESTAMP,
    external_modified_at TIMESTAMP,
    synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(external_id, source_system)
);

-- Tasks table
CREATE TABLE IF NOT EXISTS tasks (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(255) NOT NULL,
    source_system VARCHAR(50) NOT NULL,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    title VARCHAR(1000) NOT NULL,
    description TEXT,
    status VARCHAR(100),
    priority VARCHAR(20),
    assignee_id BIGINT REFERENCES users(id),
    due_date TIMESTAMP,
    external_created_at TIMESTAMP,
    external_modified_at TIMESTAMP,
    synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(external_id, source_system)
);

-- Comments table
CREATE TABLE IF NOT EXISTS comments (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(255) NOT NULL,
    source_system VARCHAR(50) NOT NULL,
    task_id BIGINT NOT NULL REFERENCES tasks(id),
    author_id BIGINT REFERENCES users(id),
    content TEXT NOT NULL,
    external_created_at TIMESTAMP,
    synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(external_id, source_system)
);

-- Sync logs table
CREATE TABLE IF NOT EXISTS sync_logs (
    id BIGSERIAL PRIMARY KEY,
    connector_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    projects_synced INTEGER DEFAULT 0,
    tasks_synced INTEGER DEFAULT 0,
    users_synced INTEGER DEFAULT 0,
    comments_synced INTEGER DEFAULT 0,
    error_message TEXT
);

-- Sync alerts table (for cross-platform discrepancy notifications)
CREATE TABLE IF NOT EXISTS sync_alerts (
    id BIGSERIAL PRIMARY KEY,
    alert_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    title VARCHAR(500) NOT NULL,
    message TEXT,
    ai_suggestion TEXT,
    ai_action_json TEXT,
    source_system VARCHAR(50),
    source_id VARCHAR(255),
    source_url VARCHAR(1000),
    target_system VARCHAR(50),
    target_id VARCHAR(255),
    target_url VARCHAR(1000),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    is_resolved BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- AI analysis state tracking
CREATE TABLE IF NOT EXISTS analysis_state (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(500) NOT NULL,
    source_system VARCHAR(50),
    content_checksum VARCHAR(64),
    last_analyzed_at TIMESTAMP
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_tasks_project_id ON tasks(project_id);
CREATE INDEX IF NOT EXISTS idx_tasks_assignee_id ON tasks(assignee_id);
CREATE INDEX IF NOT EXISTS idx_tasks_source_system ON tasks(source_system);
CREATE INDEX IF NOT EXISTS idx_comments_task_id ON comments(task_id);
CREATE INDEX IF NOT EXISTS idx_sync_logs_connector_type ON sync_logs(connector_type);
CREATE INDEX IF NOT EXISTS idx_sync_alerts_unresolved ON sync_alerts(is_resolved) WHERE is_resolved = FALSE;
CREATE INDEX IF NOT EXISTS idx_sync_alerts_unread ON sync_alerts(is_read, is_resolved) WHERE is_read = FALSE AND is_resolved = FALSE;
