# Signals POC — Architecture

## System Overview

Signals POC synchronizes project management data from Asana, Linear, and GitHub into a single database, then monitors GitHub pull requests against the synced tasks to detect and automatically resolve sync discrepancies using a locally-running AI model.

```
┌──────────┐  ┌──────────┐  ┌──────────┐
│  Asana   │  │  Linear  │  │  GitHub  │
│  REST    │  │ GraphQL  │  │  REST    │
└────┬─────┘  └────┬─────┘  └────┬─────┘
     │              │              │
     └──────────────┼──────────────┘
                    ▼
     ┌──────────────────────────────┐
     │     Spring Boot Backend       │  :8080
     │                              │
     │  ┌────────────────────────┐  │
     │  │   SyncOrchestrator     │  │  ← on-demand or scheduled sync
     │  └────────────────────────┘  │
     │  ┌────────────────────────┐  │
     │  │ SyncDiscrepancyDetector│  │  ← every 5 min, rule-based, no AI
     │  └────────────┬───────────┘  │
     │               │ publishes     │
     │               ▼ event        │
     │  ┌────────────────────────┐  │
     │  │  AiEnrichmentWorker   │  │  ← @Async, dedicated thread
     │  └────────────┬───────────┘  │
     └───────────────┼──────────────┘
                     │ /api/generate
                     ▼
          ┌─────────────────────┐
          │  Ollama  :11434     │  ← signals-poc model (llama3.1:8b base)
          └─────────────────────┘
                     │
     ┌───────────────┼──────────────┐
     │    H2 / PostgreSQL           │
     └──────────────────────────────┘
                     │
     ┌───────────────▼──────────────┐
     │     Vue.js Frontend  :5173   │
     └──────────────────────────────┘
```

---

## Package Layout

```
com.signalspoc
├── ai/                         AI layer (Ollama integration)
│   ├── client/                 OllamaClient — raw HTTP to Ollama /api/generate
│   ├── config/                 AiConfig (@ConfigurationProperties), AsyncConfig (thread pools)
│   ├── event/                  AlertEnrichmentEvent (Spring ApplicationEvent)
│   ├── model/                  AiActionRecommendation (+ ActionType enum), AnalysisState (JPA entity)
│   ├── repository/             AnalysisStateRepository
│   ├── service/                AiEnrichmentWorker, AiAnalysisScheduler, AiSuggestionService
│   └── util/                   AnalysisChecksumUtil (SHA-256 of PR + task fields)
│
├── api/                        REST layer
│   ├── controller/             AlertController, AuthController, ProjectController,
│   │                           TaskController, UserController, CommentController, SyncController
│   ├── dto/response/           AlertResponse, ProjectResponse, TaskResponse, UserResponse,
│   │                           CommentResponse, SyncResponse, SyncLogResponse
│   └── exception/              GlobalExceptionHandler (+ static inner ErrorResponse)
│
├── connector/                  External system adapters
│   ├── api/                    ConnectorService, WritableConnectorService (interfaces)
│   ├── model/                  Normalized: ConnectorProject/Task/User/Comment, SyncResult
│   ├── asana/                  AsanaConnectorService, AsanaApiClient, AsanaConfig, AsanaMapper  ← PM + write
│   ├── github/                 GitHubConnectorService (connection test only), GitHubApiClient,  ← SVC only
│   │                           GitHubConfig, GitHubMapper, dto/
│   └── linear/                 LinearConnectorService, LinearApiClient, LinearConfig, LinearMapper  ← PM + write
│
├── domain/                     Business logic
│   ├── entity/                 User, Project, Task, Comment, SyncLog, SyncAlert
│   ├── repository/             Spring Data JPA repositories (one per entity)
│   └── service/                SyncOrchestrator, SyncDiscrepancyDetector, SyncAlertService,
│                               AlertActionExecutor, ProjectService, TaskService,
│                               UserService, CommentService
│                               (SyncDiscrepancyDetector only active when connectors.github.enabled=true)
│
└── shared/
    ├── config/                 SecurityConfig, JwtAuthenticationFilter,
    │                           JwtService, RateLimitingFilter
    ├── model/Enums.java        ConnectorType (ASANA/LINEAR/GITHUB), Priority, SyncStatus
    └── exception/Exceptions.java  ConnectorException, ResourceNotFoundException, SyncException
```

---

## Core Components

### 1. Connector Layer

Each connector implements `ConnectorService`:

```java
public interface ConnectorService {
    ConnectorType getConnectorType();
    boolean testConnection();
    SyncResult syncAll();
    SyncResult syncProjects();
    SyncResult syncTasks();
    SyncResult syncUsers();
    SyncResult syncComments();
}
```

Write-capable connectors (Asana, Linear) also implement `WritableConnectorService`:

```java
public interface WritableConnectorService extends ConnectorService {
    void updateTaskStatus(String externalId, String status);
    void addComment(String entityId, String comment);
}
```

| Connector | Protocol | Auth | Role |
|---|---|---|---|
| Asana | REST | Bearer token (PAT) | PM — projects, tasks, users, comments |
| Linear | GraphQL | API key header (no Bearer prefix) | PM — projects, issues, users, comments |
| GitHub | REST | Bearer token + `X-GitHub-Api-Version` header | SVC only — PR monitoring + write-back |

> **GitHub is SVC, not PM.** `GitHubConnectorService` only handles connection testing. PR data is never stored as tasks in the database. `GitHubApiClient` is used directly by `SyncDiscrepancyDetector` (read PRs) and `AlertActionExecutor` (comment, label, approve).

Connectors are conditionally instantiated via `@ConditionalOnProperty`:
```java
@ConditionalOnProperty(name = "connectors.github.enabled", havingValue = "true")
```

`SyncOrchestrator` auto-discovers all connector beans:
```java
public SyncOrchestrator(List<ConnectorService> connectors) { ... }  // @PostConstruct builds ConnectorType→service map
```

### 2. Sync Orchestrator

`SyncOrchestrator.syncAll(ConnectorType)` runs the full sync in order:

```
fetchUsers()    → upsert into users table
fetchProjects() → upsert into projects table
fetchTasks()    → upsert into tasks table (per project)
fetchComments() → upsert into comments table (per task)
```

Upsert key: `(external_id, source_system)` — prevents duplicates across re-syncs.

### 3. Discrepancy Detection

`SyncDiscrepancyDetector` runs every 5 minutes (`@Scheduled(fixedDelay = 300000)`). It is **purely rule-based** — no AI calls, runs in milliseconds.

**Detection cases per PR:**

| Case | Condition | Alert type | Severity |
|---|---|---|---|
| 0 | PR open (not draft, not ready) + task not In Review | `PR_READY_TASK_NOT_UPDATED` | WARNING |
| 1 | PR ready to merge + task not In Review/Done | `PR_READY_TASK_NOT_UPDATED` | WARNING |
| 2 | PR merged + task not Done/Complete | `PR_MERGED_TASK_OPEN` | CRITICAL |
| 3 | PR open > 7 days | `STALE_PR` | WARNING |
| 4 | PR has no issue ID in title/body | `MISSING_LINK` | INFO |

Issue IDs are extracted from PR title and body via regex: `[A-Z]{2,10}-\d+` (e.g. `SIG-5`, `ENG-123`).

After saving each new alert, the detector publishes `AlertEnrichmentEvent` for async AI enrichment.

### 4. AI Enrichment (Event-Driven)

**Why event-driven?** Ollama calls take 5–30 seconds. Doing them synchronously inside the detection loop would stall it for minutes when multiple discrepancies are found at once.

**Flow:**

```
SyncDiscrepancyDetector
  → alertService.createAlert(alert)          // saves to DB (own @Transactional)
  → eventPublisher.publishEvent(event)        // synchronous dispatch
        ↓
  AiEnrichmentWorker.onAlertCreated(event)    // @EventListener @Async("aiEnrichmentExecutor")
    → aiSuggestionService.generateAlertSuggestion()   // plain-text Ollama call
    → aiSuggestionService.generateActionRecommendation()  // JSON Ollama call
    → alertRepository.updateAiEnrichment(id, suggestion, actionJson)
```

**Thread pool** (`aiEnrichmentExecutor`): core=1, max=2, queue=100. Single core keeps Ollama calls sequential (one model, one GPU); queue absorbs bursts.

**`enqueueIfNew()` guard:**
```java
if (saved.getAiSuggestion() == null) {   // skip deduplicated alerts already enriched
    eventPublisher.publishEvent(new AlertEnrichmentEvent(...));
}
```

### 5. AI Analysis Scheduler (Reconciliation)

`AiAnalysisScheduler` runs every 30 minutes (`@Scheduled(fixedDelayString = "${ai.ollama.reconciliation-interval-ms:1800000}")`).

**Two jobs per run:**

1. **Reconciliation** — finds alerts where `aiSuggestion IS NULL`, re-publishes enrichment events. Catches alerts the event worker missed (e.g. if Ollama was down).

2. **Semantic batch analysis** — compares PR-task pairs by SHA-256 checksum. Only analyzes pairs where something changed since the last run. Detects semantic mismatches (e.g. PR title doesn't match task) that rule-based detection can't catch.

### 6. Ollama Integration

**OllamaClient** calls `/api/generate` (non-streaming):

| Method | Temperature | Purpose |
|---|---|---|
| `generateSuggestion()` | 0.4 | 2-3 sentence plain-text suggestion |
| `generateStructuredResponse()` | 0.1 | JSON `AiActionRecommendation` |

**Token optimization:** When `model` starts with `signals-poc`, the system prompt is NOT injected into requests (saves ~200 tokens/call) — it's already baked into the model's Modelfile.

**`AiSuggestionService`** similarly skips the verbose action reference block in the prompt (~150 tokens) when using `signals-poc`.

### 7. Custom Ollama Model (`Modelfile`)

Built from `llama3.1:8b` with product-specific tuning:

```
FROM llama3.1:8b
SYSTEM "... Signals POC context, alert types, platform conventions, action types ..."
PARAMETER temperature 0.15
PARAMETER top_p 0.9
PARAMETER repeat_penalty 1.1
PARAMETER num_predict 1500
```

The SYSTEM directive bakes the product context into the model — every inference has full context without paying token cost per request.

### 8. Alert Action Executor

`AlertActionExecutor` executes `AiActionRecommendation` via connector APIs:

| Action | Implementation |
|---|---|
| `UPDATE_TASK_STATUS` | Asana: PATCH task, Linear: `issueUpdate` mutation |
| `COMPLETE_TASK` | Asana: mark complete, Linear: transition to Done |
| `ADD_COMMENT` | Asana: POST story, Linear: `commentCreate` mutation |
| `ADD_PR_COMMENT` | GitHub: POST `/repos/{owner}/{repo}/issues/{pr}/comments` |
| `UPDATE_PR_LABELS` | GitHub: PUT `/repos/{owner}/{repo}/issues/{pr}/labels` |
| `APPROVE_PR` | GitHub: POST `/repos/{owner}/{repo}/pulls/{pr}/reviews` (event: APPROVE) |

---

## Database Schema

```sql
-- Normalized users from all platforms
CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    external_id    VARCHAR(255) NOT NULL,
    source_system  VARCHAR(50)  NOT NULL,   -- ASANA, LINEAR, GITHUB
    name           VARCHAR(255) NOT NULL,
    email          VARCHAR(255),
    is_active      BOOLEAN,
    synced_at      TIMESTAMP NOT NULL,
    last_synced_at TIMESTAMP NOT NULL,
    UNIQUE(external_id, source_system)
);

-- Projects / teams
CREATE TABLE projects (
    id             BIGSERIAL PRIMARY KEY,
    external_id    VARCHAR(255) NOT NULL,
    source_system  VARCHAR(50)  NOT NULL,
    name           VARCHAR(255) NOT NULL,
    owner_id       BIGINT REFERENCES users(id),
    ...
    UNIQUE(external_id, source_system)
);

-- Tasks / issues
CREATE TABLE tasks (
    id             BIGSERIAL PRIMARY KEY,
    external_id    VARCHAR(255) NOT NULL,
    source_system  VARCHAR(50)  NOT NULL,
    project_id     BIGINT REFERENCES projects(id),
    assignee_id    BIGINT REFERENCES users(id),
    title          VARCHAR(1000) NOT NULL,
    status         VARCHAR(100),
    priority       VARCHAR(20),
    ...
    UNIQUE(external_id, source_system)
);

-- Sync discrepancy alerts
CREATE TABLE sync_alerts (
    id             BIGSERIAL PRIMARY KEY,
    alert_type     VARCHAR(50) NOT NULL,    -- PR_READY_TASK_NOT_UPDATED, PR_MERGED_TASK_OPEN,
                                            --   STALE_PR, MISSING_LINK, STATUS_MISMATCH,
                                            --   ASSIGNEE_MISMATCH
    severity       VARCHAR(20) NOT NULL,    -- CRITICAL, WARNING, INFO
    title          VARCHAR(500) NOT NULL,
    message        TEXT,
    ai_suggestion  TEXT,                    -- NULL until AiEnrichmentWorker fills it in
    ai_action_json TEXT,                    -- JSON of AiActionRecommendation
    source_system  VARCHAR(50),             -- GITHUB
    source_id      VARCHAR(255),            -- PR ID
    source_url     VARCHAR(1000),           -- PR URL
    target_system  VARCHAR(50),             -- ASANA or LINEAR
    target_id      VARCHAR(255),            -- task external ID
    is_read        BOOLEAN DEFAULT FALSE,
    is_resolved    BOOLEAN DEFAULT FALSE,
    resolved_at    TIMESTAMP,
    created_at     TIMESTAMP NOT NULL
);

-- AI analysis checksum state (for change detection)
CREATE TABLE analysis_state (
    id               BIGSERIAL PRIMARY KEY,
    entity_type      VARCHAR(50) NOT NULL,
    entity_id        VARCHAR(500) NOT NULL,
    source_system    VARCHAR(50),
    content_checksum VARCHAR(64),            -- SHA-256 of PR + task fields
    last_analyzed_at TIMESTAMP
);
```

---

## Security

### Authentication Flow

```
POST /api/v1/auth/login {"username":"admin","password":"admin123"}
  → JwtService.generateToken()
  → {"accessToken":"eyJ...","expiresIn":86400}

Subsequent requests:
  Authorization: Bearer eyJ...
  → JwtAuthenticationFilter validates signature + expiry
  → sets SecurityContext
```

### Configuration

| Variable | Description |
|---|---|
| `security.jwt.secret-key` | HMAC-SHA384 signing key (min 32 chars) |
| `security.jwt.expiration-ms` | Token lifetime (default: 86400000 = 24h) |

### Rate Limiting

`RateLimitingFilter`: 100 req/min per IP. Returns `429 Too Many Requests` with `X-Rate-Limit-Retry-After` header when exceeded.

### Security Headers

All responses include: `X-Content-Type-Options`, `Strict-Transport-Security`, `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy`.

CORS: allowed origins `http://localhost:3000`, `http://localhost:5173`, `http://localhost:8080`.

---

## Configuration Reference

`ai.ollama.*` properties (`AiConfig.java`):

| Property | Default | Description |
|---|---|---|
| `ai.ollama.enabled` | `false` | Enable AI features |
| `ai.ollama.url` | `http://localhost:11434` | Ollama base URL |
| `ai.ollama.model` | `llama3` | Model name (`signals-poc` in local profile) |
| `ai.ollama.timeout-seconds` | `30` | HTTP timeout (`60` in local profile) |
| `ai.ollama.max-tokens` | `500` | Tokens for suggestion generation |
| `ai.ollama.analysis-max-tokens` | `1500` | Tokens for structured/batch analysis |
| `ai.ollama.analysis-batch-size` | `5` | PR-task pairs per Ollama call |
| `ai.ollama.reconciliation-interval-ms` | `1800000` | Reconciliation + semantic analysis interval (30 min) |
