# Signals

> Cross-platform project-management intelligence that detects task/PR discrepancies and closes the loop automatically — with on-premise AI, human-in-the-loop approval, and pluggable connector modules.

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3-42b883.svg)](https://vuejs.org/)
[![Tests](https://img.shields.io/badge/tests-88%20passing-brightgreen.svg)](#testing)

---

## What It Does

Engineering teams lose hours every week to status drift — a PR merges but the Linear or Asana ticket still says *In Progress*, or a task is marked done but the PR is still open. Signals fixes this automatically:

1. **Ingests** projects, tasks, users and comments from Asana, Linear and GitHub on a schedule or on-demand.
2. **Detects** discrepancies between PM task state and GitHub PR state using a fast rule engine (no AI in the hot path).
3. **Enriches** every alert with an on-premise Ollama LLM recommendation (plain-text suggestion + structured action).
4. **Acts** — one-click or policy-approved write-back updates the task status, posts an audit comment, and labels the PR.

---

## How It Works

```
Asana · Linear · GitHub APIs
         │
         ▼  (scheduled every 5 min, or on-demand via REST/UI)
┌─────────────────────────────────────────────────────┐
│               Spring Boot Backend (:8080)            │
│                                                      │
│  SyncOrchestrator          ← PmConnectorService      │
│    auto-discovers connectors via Spring DI           │
│    writes to H2 (dev) / Postgres (prod)              │
│                                                      │
│  SyncDiscrepancyDetector   ← rule-based, fast        │
│    publishes AlertEnrichmentEvent                    │
│                                                      │
│  AiEnrichmentWorker        ← @Async, off hot-path    │
│    calls Ollama → patches alert with suggestion      │
│                                                      │
│  AlertActionExecutor       ← write-back              │
│    updateTaskStatus / addComment / completeTask      │
└─────────────────────────────────────────────────────┘
         │
         ▼
  Ollama (:11434) — signals-poc model (llama3.1:8b + Modelfile)
         │
         ▼
  Vue 3 Frontend (:5173) — dashboard, alerts, one-click approval
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.2.2, Spring Security |
| ORM | Spring Data JPA + Hibernate, Flyway migrations |
| AI | Ollama (local LLM), custom `signals-poc` model (llama3.1:8b) |
| Database | H2 in-memory (local dev) / PostgreSQL 16 (production) |
| Auth | JWT (stateless) + BCrypt + rate limiting (Bucket4j) |
| API Docs | OpenAPI 3 / Swagger UI |
| Frontend | Vue 3, Vite, Pinia, Vue Router |
| Build | Maven 3.8+, multi-stage Docker, Docker Compose |
| Testing | JUnit 5, Mockito (strict), AssertJ — 88 tests |

---

## Project Structure

```
signals-poc/
├── LICENSE
├── README.md
├── run.sh                          # Full-stack launcher (Ollama + backend + frontend)
├── docker-compose.yml              # Production stack (Postgres + Ollama + backend + frontend)
│
├── ai/
│   └── Modelfile                   # Custom Ollama model definition (signals-poc)
│
├── backend/
│   ├── Dockerfile                  # Multi-stage: Maven build → slim JRE image
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/signalspoc/
│       │   │   ├── SignalsApplication.java
│       │   │   ├── ai/             # Ollama client, enrichment worker, scheduler
│       │   │   ├── api/            # REST controllers (7), DTOs, GlobalExceptionHandler
│       │   │   ├── connector/
│       │   │   │   ├── api/        # ConnectorService (base interface)
│       │   │   │   ├── model/      # ConnectorProject/Task/User/Comment, SyncResult
│       │   │   │   ├── pm/         # PM module — PmConnectorService interface
│       │   │   │   │   ├── api/    #   syncAll/Tasks/Projects + updateStatus/addComment/completeTask
│       │   │   │   │   ├── asana/  #   AsanaConnectorService, ApiClient, Mapper, Config, DTOs
│       │   │   │   │   └── linear/ #   LinearConnectorService, ApiClient, Mapper, Config, DTOs
│       │   │   │   ├── svc/        # SVC module — SvcConnectorService (stub, Phase 2)
│       │   │   │   │   └── api/    #   GitHub, GitLab, Bitbucket will implement this
│       │   │   │   ├── notification/ # Notification module — stub (Phase 2)
│       │   │   │   │   └── api/    #   Slack, Teams, Discord will implement this
│       │   │   │   └── github/     # GitHubConnectorService (ConnectorService — PR monitoring)
│       │   │   ├── domain/         # Business logic
│       │   │   │   ├── entity/     # User, Project, Task, Comment, SyncLog, SyncAlert
│       │   │   │   ├── repository/ # Spring Data JPA repositories
│       │   │   │   └── service/    # SyncOrchestrator, SyncDiscrepancyDetector,
│       │   │   │                   # SyncAlertService, AlertActionExecutor, ...
│       │   │   └── shared/
│       │   │       ├── config/     # SecurityConfig, JwtAuthFilter, RateLimitingFilter
│       │   │       ├── model/      # Enums.java (ConnectorType, Priority, SyncStatus)
│       │   │       └── exception/  # Exceptions.java (ConnectorException, ResourceNotFoundException)
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-local.yml  # H2, all connectors via env vars (gitignored)
│       │       └── application-prod.yml   # Postgres, SSL, minimal logging
│       └── test/
│           └── java/com/signalspoc/       # 88 unit tests
│
└── frontend/
    ├── Dockerfile                  # Multi-stage: Vite build → nginx:alpine
    ├── nginx.conf                  # SPA fallback + /api proxy to backend
    ├── vite.config.js
    └── src/
        ├── views/                  # Dashboard, Projects, Tasks, Users, Sync, Alerts, Login
        ├── stores/                 # Pinia (auth store)
        ├── api/                    # Axios client with JWT interceptor
        └── router/
```

---

## Quick Start — Local (No Docker)

### Prerequisites

- Java 17 (`JAVA_HOME` must point to JDK 17 or 19 — Lombok is incompatible with JDK 21+)
- Maven 3.8+
- Node.js 18+
- [Ollama](https://ollama.com) — for AI features (optional but recommended)

```bash
./run.sh
```

`run.sh` automatically:
1. Starts Ollama if not already running
2. Builds the `signals-poc` model from `ai/Modelfile` (pulls `llama3.1:8b` if needed)
3. Compiles and starts the Spring Boot backend on the `local` profile (H2 in-memory DB)
4. Installs npm deps and starts the Vite dev server

**Access points after startup:**

| Service | URL |
|---|---|
| Frontend | http://localhost:5173 |
| REST API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| H2 Console | http://localhost:8080/h2-console |
| Health check | http://localhost:8080/actuator/health |
| Ollama | http://localhost:11434 |

**Default credentials:** `admin / admin123` or `user / user123`

---

## Connector Setup

Copy `backend/src/main/resources/application-local.yml` (gitignored — never committed) and set your keys:

```yaml
connectors:
  asana:
    enabled: true
    api-key: ${ASANA_API_KEY:your-asana-pat}      # Settings → Apps → Personal Access Tokens

  linear:
    enabled: true
    api-key: ${LINEAR_API_KEY:your-linear-key}    # Settings → API → Personal API keys

  github:
    enabled: true
    token: ${GITHUB_TOKEN:your-github-pat}         # Developer Settings → PAT (repo scope)
    repositories: ${GITHUB_REPOSITORIES:owner/repo}  # comma-separated

ai:
  ollama:
    enabled: true
    model: ${OLLAMA_MODEL:signals-poc}
```

> Restart the backend after changing connector config — Spring loads it once at startup.

---

## Production — Docker Compose

```bash
# 1. Create .env (never commit this file)
cat > .env <<EOF
DB_USERNAME=signals_user
DB_PASSWORD=change_me_in_prod
JWT_SECRET_KEY=change_me_min_32_chars_in_prod
ASANA_ENABLED=true
ASANA_API_KEY=your_asana_pat
LINEAR_ENABLED=true
LINEAR_API_KEY=your_linear_key
GITHUB_ENABLED=true
GITHUB_TOKEN=your_github_pat
GITHUB_REPOSITORIES=owner/repo
OLLAMA_MODEL=signals-poc
BASE_MODEL=llama3.1:8b
EOF

# 2. Start the full stack
docker compose up -d
```

Services started:

| Container | Port | Description |
|---|---|---|
| `signals-postgres` | 5432 | PostgreSQL 16 with health check |
| `signals-ollama` | 11434 | Ollama LLM server |
| `signals-ollama-init` | — | Pulls base model, builds signals-poc (runs once, exits) |
| `signals-backend` | 8080 | Spring Boot API |
| `signals-frontend` | 80 | Vue 3 SPA via nginx |

---

## Connector Architecture

Each connector module is completely isolated. Adding a new PM connector (e.g. Jira) requires:

1. Create `connector/pm/jira/` — implement `PmConnectorService`
2. Add `@ConditionalOnProperty(name = "connectors.jira.enabled")`
3. Add config to `application.yml`

`SyncOrchestrator` and `AlertActionExecutor` auto-discover the new connector via `List<PmConnectorService>`. **Zero other files change.**

```
ConnectorService (base — identity + connectivity test)
├── PmConnectorService         ← PM tools: sync + write-back
│   ├── AsanaConnectorService  ✓ live
│   └── LinearConnectorService ✓ live
│   └── JiraConnectorService   (roadmap)
├── SvcConnectorService        ← SVC tools: PR/branch data (Phase 2)
│   └── GitHubConnectorService (partial — PR monitoring only)
│   └── GitLabConnectorService (roadmap)
└── NotificationConnectorService ← Alerts delivery (Phase 2)
    └── SlackConnectorService  (roadmap)
    └── TeamsConnectorService  (roadmap)
```

---

## AI Architecture

Ollama is **never** in the critical path for alert detection:

```
SyncDiscrepancyDetector  — rule-based, every 5 min, milliseconds
  └── saves SyncAlert to DB
  └── publishes AlertEnrichmentEvent
          │
          ▼ (async, dedicated thread pool)
    AiEnrichmentWorker
      ├── calls Ollama → plain-text aiSuggestion
      └── calls Ollama → structured AiActionRecommendation (JSON)
            └── AlertActionExecutor executes on approval

AiAnalysisScheduler  — every 30 min
  └── reconciles alerts with null aiSuggestion (missed events)
  └── deep semantic analysis on changed PR-task pairs (SHA-256 diff)
```

### Custom Model

```bash
# Build manually (run.sh does this automatically)
ollama create signals-poc -f ai/Modelfile
```

| Parameter | llama3.1:8b base | signals-poc |
|---|---|---|
| temperature | ~0.8 | 0.15 (deterministic) |
| System prompt | none | product-tuned (baked in) |
| Token saving | — | ~200 tokens/call (prompt in weights) |

### Write-back Actions

| Action | Effect |
|---|---|
| `UPDATE_TASK_STATUS` | Moves Asana task / Linear issue to a new status |
| `COMPLETE_TASK` | Marks task as done |
| `ADD_COMMENT` | Posts comment to Asana task or Linear issue |
| `ADD_PR_COMMENT` | Posts comment on GitHub PR |
| `UPDATE_PR_LABELS` | Sets labels on GitHub PR |
| `APPROVE_PR` | Submits approved review on GitHub |
| `NO_ACTION` | No action needed (audit comment still posted) |
| `MANUAL_REVIEW` | Flags for human review |

---

## API Reference

### Authentication

```bash
# Obtain JWT token
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
# → {"accessToken":"eyJ...","tokenType":"Bearer","expiresIn":86400}

# Use token on all subsequent requests
curl http://localhost:8080/api/v1/tasks \
  -H "Authorization: Bearer eyJ..."
```

### Sync

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/sync/{connector}/test` | Test connector connectivity |
| `POST` | `/api/v1/sync/{connector}/all` | Full sync (projects + tasks + users + comments) |
| `POST` | `/api/v1/sync/{connector}/projects` | Sync projects only |
| `POST` | `/api/v1/sync/{connector}/tasks` | Sync tasks only |
| `POST` | `/api/v1/sync/{connector}/users` | Sync users only |
| `POST` | `/api/v1/sync/{connector}/comments` | Sync comments only |
| `GET` | `/api/v1/sync/logs` | Paginated sync history |

`{connector}`: `asana`, `linear` for sync operations; `github` for test only.

### Alerts

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/alerts` | All unresolved alerts (paginated) |
| `GET` | `/api/v1/alerts/unread` | Unread alerts |
| `GET` | `/api/v1/alerts/count` | Unread count (for badge) |
| `POST` | `/api/v1/alerts/{id}/read` | Mark as read |
| `POST` | `/api/v1/alerts/{id}/resolve` | Resolve alert |
| `POST` | `/api/v1/alerts/{id}/approve` | Execute AI-recommended action |

### Data

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/projects` | List projects |
| `GET` | `/api/v1/tasks` | List tasks |
| `GET` | `/api/v1/tasks/project/{projectId}` | Tasks for a project |
| `GET` | `/api/v1/users` | List users |
| `GET` | `/api/v1/comments/task/{taskId}` | Comments for a task |

**Common query params:** `sourceSystem` (ASANA / LINEAR / GITHUB), `page`, `size`, `sort`

Full interactive docs: **http://localhost:8080/swagger-ui.html**

---

## Security

| Control | Implementation |
|---|---|
| Authentication | Stateless JWT (HS256, 24 h expiry) |
| Password hashing | BCrypt |
| Rate limiting | Bucket4j — 10 req/min per user per endpoint family |
| CORS | Explicit allowlist (5173, 8080) |
| Headers | CSP, X-Content-Type-Options, Referrer-Policy, Permissions-Policy |
| Session | Stateless — no server-side session |
| 401 responses | JSON body, no `WWW-Authenticate` header (prevents browser popup) |
| AI actions | Human approval required by default; all actions audit-logged |
| Secrets | Never committed — loaded from env vars or gitignored local YAML |

---

## Testing

```bash
cd backend
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-19.jdk/Contents/Home \
  mvn test
```

88 tests across 7 test classes, all passing:

| Test class | Coverage |
|---|---|
| `AsanaConnectorServiceTest` | Sync, mapping, error handling |
| `LinearConnectorServiceTest` | GraphQL queries, mapping, errors |
| `SyncOrchestratorTest` | Multi-connector orchestration, PM-only routing |
| `SyncDiscrepancyDetectorTest` | All 4 alert cases (PR ready, closed, missing, stale) |
| `AlertActionExecutorTest` | All action types, write-back, audit comments |
| `TaskServiceTest` | CRUD, assignee resolution, project linking |
| `SyncControllerTest` | Auth (401), PM-only routing (400 for GitHub sync), 500 on error |

Rate limiting is disabled in the test profile (`application-test.yml`) to prevent bucket exhaustion.

---

## Environment Variables

| Variable | Description | Required |
|---|---|---|
| `ASANA_API_KEY` | Asana Personal Access Token | For Asana |
| `LINEAR_API_KEY` | Linear API key (`lin_api_...`) | For Linear |
| `GITHUB_TOKEN` | GitHub PAT (`repo` scope) | For GitHub |
| `GITHUB_REPOSITORIES` | Comma-separated `owner/repo` list | For GitHub |
| `OLLAMA_MODEL` | Model name | No (default: `signals-poc`) |
| `AI_OLLAMA_URL` | Ollama base URL | No (default: `http://localhost:11434`) |
| `BASE_MODEL` | Base model for Docker init | No (default: `llama3.1:8b`) |
| `DB_USERNAME` | Postgres username | Production |
| `DB_PASSWORD` | Postgres password | Production |
| `JWT_SECRET_KEY` | JWT signing key (min 32 chars) | Production |
| `SERVER_PORT` | Backend port override | No (default: `8080`) |

---

