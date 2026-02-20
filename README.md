# Signals POC

A cross-platform project management synchronization system that monitors GitHub pull requests against Asana and Linear tasks, detects sync discrepancies, and uses a locally-running Ollama AI model to recommend and execute automated fixes.

## How It Works

```
Asana / Linear / GitHub APIs
         │
         ▼ (sync every 5 min or on demand)
  Spring Boot Backend (:8080)
    ├── SyncOrchestrator       — pulls tasks, projects, users, comments into H2/Postgres
    ├── SyncDiscrepancyDetector — rule-based PR↔task mismatch detection (no AI, fast)
    │       └── publishes AlertEnrichmentEvent on new alert
    └── AiEnrichmentWorker     — async, picks up event, calls Ollama, patches alert
         │
         ▼
  Ollama (:11434) — signals-poc custom model (llama3.1:8b base, tuned Modelfile)
         │
         ▼
  Vue.js Frontend (:5173) — dashboard, alerts, one-click action execution
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.2.2 |
| AI | Ollama (local), custom `signals-poc` model (llama3.1:8b base) |
| Database | H2 (local dev) / PostgreSQL (production) |
| ORM | Spring Data JPA + Hibernate |
| API Docs | OpenAPI 3 / Swagger UI |
| Frontend | Vue 3 + Vite + Pinia |
| Auth | JWT (stateless) + HTTP Basic fallback |

---

## Quick Start (Local — No Docker Required)

### Prerequisites

- Java 17 (must be JDK 17, not 19/21 — Lombok requires it)
- Maven 3.8+
- Node.js 18+
- Ollama ([ollama.com](https://ollama.com)) — for AI features

```bash
./run.sh
```

`run.sh` automatically:
1. Starts Ollama if not already running
2. Builds the product-tuned `signals-poc` model from `Modelfile` (pulls `llama3.1:8b` if needed)
3. Compiles and starts the Spring Boot backend (local profile, H2 in-memory DB)
4. Installs npm deps and starts the Vite dev server

**Access points:**

| Service | URL |
|---|---|
| Frontend | http://localhost:5173 |
| REST API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| H2 Console | http://localhost:8080/h2-console |
| Health | http://localhost:8080/actuator/health |
| Ollama | http://localhost:11434 |

**Default credentials:** `admin / admin123` or `user / user123`

---

## Connector Setup

Configure API keys in `src/main/resources/application-local.yml` (local dev only):

```yaml
connectors:
  asana:
    enabled: true
    api-key: ${ASANA_PAT:your-asana-pat-here}   # Settings → Apps → Personal Access Tokens

  linear:
    enabled: true
    api-key: ${LINEAR_API_KEY:your-linear-key}   # Settings → API → Personal API keys

  github:
    enabled: true
    token: ${GITHUB_TOKEN:your-github-pat}        # Settings → Developer Settings → PAT (needs: repo scope)
    repositories: ${GITHUB_REPOSITORIES:owner/repo-name}  # comma-separated

ai:
  ollama:
    enabled: true
    model: ${OLLAMA_MODEL:signals-poc}            # custom model built from Modelfile
```

> **Important:** Restart the backend after changing any values — Spring Boot loads config once at startup.

---

## Production Setup (Docker Compose)

```bash
# 1. Create .env file
cat > .env <<EOF
DB_USERNAME=signals_user
DB_PASSWORD=signals_pass
ASANA_ENABLED=true
ASANA_API_KEY=your_asana_pat
LINEAR_ENABLED=true
LINEAR_API_KEY=your_linear_key
GITHUB_ENABLED=true
GITHUB_TOKEN=your_github_pat
GITHUB_REPOSITORIES=owner/repo
BASE_MODEL=llama3.1:8b        # base model to pull before building signals-poc
EOF

# 2. Start everything
docker compose up -d
```

On first start, `ollama-init` automatically pulls the base model and builds the `signals-poc` model from `Modelfile`. Subsequent starts reuse the cached model from the `ollama_data` volume.

---

## AI Architecture

The AI layer is event-driven — Ollama is **never** in the critical path of detection:

```
SyncDiscrepancyDetector (every 5 min, rule-based, milliseconds)
  ↓ saves alert to DB
  ↓ publishes AlertEnrichmentEvent
        ↓
    AiEnrichmentWorker (@Async, dedicated thread pool)
      → calls Ollama for plain-text suggestion
      → calls Ollama for structured AiActionRecommendation (JSON)
      → patches alert in DB with both results

AiAnalysisScheduler (every 30 min, @Scheduled)
  → reconciles alerts that still have null aiSuggestion (missed by event worker)
  → runs deep semantic batch analysis on changed PR-task pairs (SHA-256 checksum)
```

### Custom Ollama Model

The `signals-poc` model is built from `Modelfile` at the project root:

```bash
# Build manually (run.sh does this automatically)
ollama create signals-poc -f Modelfile
```

Key tuning vs base llama3.1:8b:

| Parameter | Base default | signals-poc |
|---|---|---|
| temperature | ~0.8 | 0.15 (deterministic) |
| top_p | 0.9 | 0.9 |
| repeat_penalty | 1.1 | 1.1 |
| System prompt | none | product-specific (baked in) |

When using `signals-poc`, the system prompt is **not** re-sent on each request (it's already in the model weights) — saving ~200 input tokens per call.

### Action Types

When Ollama recommends an action, `AlertActionExecutor` can execute it automatically:

| Action | Effect |
|---|---|
| `UPDATE_TASK_STATUS` | Moves Asana task / Linear issue to a new status |
| `COMPLETE_TASK` | Marks task as done/complete |
| `ADD_COMMENT` | Posts a comment to an Asana task or Linear issue |
| `ADD_PR_COMMENT` | Posts a comment on the GitHub PR |
| `UPDATE_PR_LABELS` | Sets labels on the GitHub PR |
| `APPROVE_PR` | Submits an approved review on GitHub |
| `NO_ACTION` | No automated action needed |
| `MANUAL_REVIEW` | Flags for human review |

---

## Project Structure

```
signals-poc/
├── Modelfile                              # Ollama model definition (signals-poc)
├── run.sh                                 # Full stack launcher (Ollama + backend + frontend, local dev)
├── run-local.sh                           # Alternative local launcher (H2, optional Ollama)
├── docker-compose.yml                     # Production: Postgres + Ollama + backend + frontend
│
├── src/main/java/com/signalspoc/
│   ├── SignalsApplication.java            # Main entry (@EnableAsync @EnableScheduling)
│   │
│   ├── ai/                               # AI layer
│   │   ├── client/OllamaClient.java      # Ollama /api/generate calls
│   │   ├── config/AiConfig.java          # ai.ollama.* properties
│   │   ├── config/AsyncConfig.java       # Thread pools (aiAnalysisExecutor, aiEnrichmentExecutor)
│   │   ├── event/AlertEnrichmentEvent.java  # Spring event: new alert → enrich with AI
│   │   ├── model/AiActionRecommendation.java
│   │   ├── model/AnalysisState.java      # Checksum state for change detection
│   │   ├── repository/AnalysisStateRepository.java
│   │   ├── service/AiEnrichmentWorker.java  # @Async event listener → calls Ollama → patches alert
│   │   ├── service/AiAnalysisScheduler.java # 30-min reconciliation + semantic batch analysis
│   │   ├── service/AiSuggestionService.java # Prompt building + Ollama call orchestration
│   │   └── util/AnalysisChecksumUtil.java
│   │
│   ├── api/                              # REST layer
│   │   ├── controller/                   # AlertController, AuthController, ProjectController,
│   │   │                                 # TaskController, UserController, CommentController,
│   │   │                                 # SyncController
│   │   ├── dto/response/                 # Response DTOs
│   │   └── exception/GlobalExceptionHandler.java
│   │
│   ├── connector/                        # External API adapters
│   │   ├── api/                          # ConnectorService, WritableConnectorService interfaces
│   │   ├── model/                        # Normalized: ConnectorProject/Task/User/Comment, SyncResult
│   │   ├── asana/                        # REST API adapter
│   │   ├── github/                       # REST API adapter (PR monitoring + write-back)
│   │   └── linear/                       # GraphQL adapter
│   │
│   ├── domain/                           # Business logic
│   │   ├── entity/                       # JPA: User, Project, Task, Comment, SyncLog, SyncAlert
│   │   ├── repository/                   # Spring Data repositories
│   │   └── service/                      # SyncOrchestrator, SyncDiscrepancyDetector,
│   │                                     # SyncAlertService, AlertActionExecutor, ...
│   │
│   └── shared/
│       ├── config/                       # SecurityConfig, JwtAuthFilter, RateLimitingFilter
│       ├── model/Enums.java              # ConnectorType, Priority, SyncStatus
│       └── exception/Exceptions.java     # ConnectorException, ResourceNotFoundException, SyncException
│
└── frontend/                             # Vue 3 SPA
    └── src/
        ├── views/                        # Dashboard, Projects, Tasks, Users, Sync, Alerts, Login
        ├── stores/                       # Pinia (auth)
        ├── api/                          # Axios client
        └── router/
```

---

## API Reference

### Authentication

```bash
# Get JWT token
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
# → {"accessToken":"eyJ...","tokenType":"Bearer","expiresIn":86400}

# Use token
curl http://localhost:8080/api/v1/tasks \
  -H "Authorization: Bearer eyJ..."
```

### Sync

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/sync/{connector}/test` | Test connector connection |
| `POST` | `/api/v1/sync/{connector}/all` | Full sync (users + projects + tasks + comments) |
| `POST` | `/api/v1/sync/{connector}/projects` | Sync projects only |
| `POST` | `/api/v1/sync/{connector}/tasks` | Sync tasks only |
| `POST` | `/api/v1/sync/{connector}/users` | Sync users only |
| `POST` | `/api/v1/sync/{connector}/comments` | Sync comments only |
| `GET` | `/api/v1/sync/logs` | Sync history |

`{connector}`: `asana`, `linear`, `github`

### Alerts

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/alerts` | All unresolved alerts (paginated) |
| `GET` | `/api/v1/alerts/unread` | Unread alerts |
| `GET` | `/api/v1/alerts/count` | Unread count (for badge) |
| `POST` | `/api/v1/alerts/{id}/read` | Mark as read |
| `POST` | `/api/v1/alerts/{id}/resolve` | Mark as resolved |
| `POST` | `/api/v1/alerts/{id}/approve` | Execute AI-recommended action |

### Data

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/projects` | List projects |
| `GET` | `/api/v1/tasks` | List tasks |
| `GET` | `/api/v1/tasks/project/{projectId}` | Tasks for a project |
| `GET` | `/api/v1/users` | List users |
| `GET` | `/api/v1/comments/task/{taskId}` | Comments for a task |

**Common query params:** `sourceSystem` (ASANA/LINEAR/GITHUB), `page`, `size`, `sort`

---

## Environment Variables

| Variable | Description | Default |
|---|---|---|
| `ASANA_PAT` | Asana Personal Access Token | — |
| `LINEAR_API_KEY` | Linear API key (`lin_api_...`) | — |
| `GITHUB_TOKEN` | GitHub PAT (`repo` scope required) | — |
| `GITHUB_REPOSITORIES` | Comma-separated `owner/repo` list | — |
| `OLLAMA_ENABLED` | Enable AI features | `true` |
| `OLLAMA_URL` | Ollama base URL | `http://localhost:11434` |
| `OLLAMA_MODEL` | Model name to use | `signals-poc` |
| `BASE_MODEL` | Base model for Docker build | `llama3.1:8b` |
| `DB_USERNAME` | Postgres username (production) | — |
| `DB_PASSWORD` | Postgres password (production) | — |
| `JWT_SECRET_KEY` | JWT signing key (min 32 chars) | dev key |

---

## Documentation

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — detailed component design and data flow
- [docs/API.md](docs/API.md) — complete API reference with request/response examples
- Swagger UI: http://localhost:8080/swagger-ui.html (interactive, always up to date)

---

## License

MIT
