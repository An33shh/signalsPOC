# Signals POC API Documentation

## Overview

The Signals POC API provides a unified interface for synchronizing project management data across multiple platforms (Asana, Linear, GitHub) and monitoring cross-platform discrepancies with AI-powered alerts.

**Base URL:** `http://localhost:8080/api/v1`

**Authentication:** JWT Bearer Token (obtained via `/api/v1/auth/login`)

**Interactive API Docs:** Available at `/swagger-ui.html` when the server is running.

---

## Authentication

### POST /api/v1/auth/login

Authenticate and obtain a JWT token.

**Request Body:**
```json
{
  "username": "string (3-50 chars)",
  "password": "string (6-100 chars)"
}
```

**Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "username": "admin"
}
```

**Usage:**
Include the token in subsequent requests:
```
Authorization: Bearer <accessToken>
```

---

## Projects

### GET /api/v1/projects

Get all projects with optional filters.

**Query Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| sourceSystem | string | Filter by source: ASANA, LINEAR |
| status | string | Filter by project status |
| search | string | Search in project name |
| page | int | Page number (default: 0) |
| size | int | Page size (default: 20) |

**Response:** Paginated list of projects

### GET /api/v1/projects/{id}

Get project by internal ID.

### GET /api/v1/projects/{connector}/{externalId}

Get project by connector type and external ID.

---

## Tasks

### GET /api/v1/tasks

Get all tasks with optional filters.

**Query Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| sourceSystem | string | Filter by source: ASANA, LINEAR |
| status | string | Filter by task status |
| priority | string | Filter by priority: LOW, MEDIUM, HIGH, CRITICAL |
| projectId | long | Filter by project ID |
| search | string | Search in task title |
| page | int | Page number (default: 0) |
| size | int | Page size (default: 20) |

**Response:** Paginated list of tasks

### GET /api/v1/tasks/{id}

Get task by internal ID.

### GET /api/v1/tasks/{connector}/{externalId}

Get task by connector type and external ID.

### GET /api/v1/tasks/project/{projectId}

Get all tasks for a specific project.

---

## Users

### GET /api/v1/users

Get all users with optional filters.

**Query Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| sourceSystem | string | Filter by source: ASANA, LINEAR |
| page | int | Page number (default: 0) |
| size | int | Page size (default: 20) |

### GET /api/v1/users/{id}

Get user by internal ID.

### GET /api/v1/users/{connector}/{externalId}

Get user by connector type and external ID.

---

## Comments

### GET /api/v1/comments

Get all comments.

### GET /api/v1/comments/{id}

Get comment by internal ID.

### GET /api/v1/comments/{connector}/{externalId}

Get comment by connector type and external ID.

### GET /api/v1/comments/task/{taskId}

Get all comments for a specific task.

---

## Sync Operations

### POST /api/v1/sync/{connector}/all

Sync all data (projects, tasks, users, comments) from a connector.

**Path Parameters:**
- `connector`: ASANA, LINEAR

**Response:**
```json
{
  "success": true,
  "projectsSynced": 10,
  "tasksSynced": 45,
  "usersSynced": 12,
  "commentsSynced": 89,
  "errors": []
}
```

### POST /api/v1/sync/{connector}/projects

Sync only projects from a connector.

### POST /api/v1/sync/{connector}/tasks

Sync only tasks from a connector.

### POST /api/v1/sync/{connector}/users

Sync only users from a connector.

### POST /api/v1/sync/{connector}/comments

Sync only comments from a connector.

### GET /api/v1/sync/{connector}/test

Test connection to a connector.

**Response:**
```json
{
  "connector": "ASANA",
  "connected": true,
  "message": "Connection successful"
}
```

### GET /api/v1/sync/logs

Get sync operation logs.

**Query Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| connectorType | string | Filter by connector |
| page | int | Page number (default: 0) |
| size | int | Page size (default: 20) |

### GET /api/v1/sync/logs/{id}

Get a specific sync log by ID.

---

## Alerts (Cross-Platform Discrepancy Monitoring)

The alert system monitors GitHub PRs against Asana/Linear tasks and creates AI-powered notifications when discrepancies are detected.

### Alert Types

| Type | Description |
|------|-------------|
| PR_READY_TASK_NOT_UPDATED | PR is ready to merge but linked task status not updated |
| PR_MERGED_TASK_OPEN | PR was merged but linked task is still open |
| TASK_COMPLETED_NO_PR | Task marked complete but no associated PR |
| STALE_PR | PR has been open for more than 7 days |
| MISSING_LINK | PR has no linked Asana/Linear task |
| STATUS_MISMATCH | PR and task statuses are inconsistent |
| ASSIGNEE_MISMATCH | PR author and task assignee don't match |

### GET /api/v1/alerts

Get unresolved alerts.

**Query Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| page | int | Page number (default: 0) |
| size | int | Page size (default: 20) |

**Response:**
```json
{
  "content": [
    {
      "id": 1,
      "alertType": "PR_READY_TASK_NOT_UPDATED",
      "severity": "WARNING",
      "title": "PR ready but task not updated",
      "message": "PR #123 'Add login feature' is ready to merge but Asana task 'Login Feature' status is 'In Progress'",
      "aiSuggestion": "Update the Asana task 'Login Feature' to 'In Review' status to reflect the current PR state.",
      "sourceSystem": "GITHUB",
      "sourceId": "12345",
      "sourceUrl": "https://github.com/org/repo/pull/123",
      "targetSystem": "ASANA",
      "targetId": "1234567890",
      "targetUrl": null,
      "isRead": false,
      "isResolved": false,
      "createdAt": "2026-02-08T10:30:00"
    }
  ],
  "totalElements": 5,
  "totalPages": 1
}
```

### GET /api/v1/alerts/unread

Get unread and unresolved alerts.

### GET /api/v1/alerts/count

Get count of unread alerts.

**Response:**
```json
{
  "count": 3
}
```

### POST /api/v1/alerts/{id}/read

Mark an alert as read.

### POST /api/v1/alerts/{id}/resolve

Resolve (dismiss) an alert.

---

## Error Responses

All endpoints return standard error responses:

```json
{
  "timestamp": "2026-02-08T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid connector type: xyz",
  "path": "/api/v1/sync/xyz/all"
}
```

**Common HTTP Status Codes:**
- `200` - Success
- `201` - Created
- `400` - Bad Request (validation error)
- `401` - Unauthorized (missing/invalid token)
- `403` - Forbidden (insufficient permissions)
- `404` - Not Found
- `429` - Too Many Requests (rate limit exceeded)
- `500` - Internal Server Error

---

## Rate Limiting

API requests are rate-limited to prevent abuse:
- **Limit:** 100 requests per minute per IP
- **Header:** `X-RateLimit-Remaining` shows remaining requests
- **Response:** `429 Too Many Requests` when limit exceeded

---

## Pagination

All list endpoints support pagination:

**Request Parameters:**
- `page` - Zero-indexed page number
- `size` - Number of items per page (max: 100)
- `sort` - Sort field and direction (e.g., `createdAt,desc`)

**Response Format:**
```json
{
  "content": [...],
  "totalElements": 100,
  "totalPages": 5,
  "number": 0,
  "size": 20,
  "first": true,
  "last": false
}
```
