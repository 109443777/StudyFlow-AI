# StudyFlow Static Test Console Design

## Goal

Build a single-page static frontend under the existing Spring Boot backend so the current StudyFlow AI backend capabilities can be exercised and demonstrated from one browser page without introducing a separate frontend project.

## Scope

This page is a testing and demo console, not a production user-facing UI.

Included capabilities:
- system health check
- user registration
- user login
- current user info query
- normal material upload
- chunk upload flow testing
- material detail query
- my materials query
- parse task query
- material content query
- material summary query and summary generation
- media transcript query
- QA session creation
- material-based QA ask and history query
- review outline generation
- exam study plan generation
- study plan history and detail query
- task failure record query and compensation

Out of scope:
- polished product UI
- Vue or separate frontend build tooling
- OCR or media preview
- visual analytics dashboards
- generic API explorer for every endpoint in Swagger

## Architecture

The frontend will be served directly by Spring Boot from `src/main/resources/static`. The page will use plain HTML, CSS, and vanilla JavaScript with `fetch` to call backend APIs. Authentication state will be stored in `localStorage`, and a small API wrapper will inject the `Authorization` header automatically.

To keep the code maintainable without introducing a framework, the frontend will be split into three files:
- `index.html` for semantic page structure
- `styles.css` for layout and visual treatment
- `app.js` for API calls, state handling, and section rendering

## UI Structure

The page will be a single dashboard-style console with vertically stacked cards.

Sections:
1. System Status
2. Authentication
3. Material Upload
4. Parse and Content Query
5. RAG QA
6. Study Support
7. Failure Records
8. Global Request Log

Each section will contain:
- a compact form
- one or more action buttons
- a result panel showing formatted JSON or key fields

## Visual Direction

The page should look deliberate but lightweight:
- warm light background instead of default white
- clear section cards
- monospace request log area
- restrained accent color for actions and status labels
- mobile-friendly stacked layout

This is still a testing console, so usability and readability take priority over decorative design.

## Data Flow

### Authentication

- Register submits `POST /api/auth/register`
- Login submits `POST /api/auth/login`
- On success, save JWT token to `localStorage`
- Current user info uses `GET /api/users/me`

### Material Flow

- Normal upload submits `POST /api/materials/upload`
- Chunk upload flow uses:
  - `POST /api/material-uploads/init`
  - `POST /api/material-uploads/chunk`
  - `GET /api/material-uploads/{uploadId}/chunks`
  - `POST /api/material-uploads/complete`
- Material query uses:
  - `GET /api/materials/{materialId}`
  - `GET /api/materials/my`

### Parse and Content Flow

- Parse tasks query uses:
  - `GET /api/parse-tasks/{taskId}`
  - `GET /api/parse-tasks`
- Material content query uses:
  - `GET /api/material-contents?materialId=...`
- Material summary uses:
  - `GET /api/material-summaries?materialId=...`
  - `POST /api/material-summaries/{materialId}/generate`
- Media transcript uses:
  - `GET /api/media-transcripts?materialId=...`

### QA Flow

- Create session uses `POST /api/qa/sessions`
- Ask question uses `POST /api/qa/sessions/{sessionId}/ask`
- Query messages uses `GET /api/qa/sessions/{sessionId}/messages`

### Study Support Flow

- Generate review outline uses `POST /api/study-plans/review-outline`
- Generate exam plan uses `POST /api/study-plans/exam-plan`
- Query plan detail uses `GET /api/study-plans/{planId}`
- Query plan history uses `GET /api/study-plans`

### Failure Governance Flow

- Query failure records uses `GET /api/task-failures`
- Compensate failure uses `POST /api/task-failures/{recordId}/compensate`

## JavaScript Design

`app.js` will be organized into focused helpers instead of one large script.

Planned internal structure:
- config constants for endpoint paths
- DOM helpers for reading fields and rendering JSON
- token storage helpers
- shared `apiRequest()` wrapper
- one handler per feature group
- page bootstrap that binds listeners on `DOMContentLoaded`

The code should avoid framework-style complexity. The only shared state is:
- current JWT token
- latest IDs for convenience, such as `materialId`, `uploadId`, and `sessionId`

## Error Handling

All requests will route through a common request helper that:
- appends JWT token when present
- parses JSON safely
- surfaces backend `Result` messages
- writes success and failure details into the global request log

UI-level handling:
- show inline result JSON for each section
- keep failed requests visible instead of silently clearing content
- warn clearly when token is missing for protected endpoints

## Testing Strategy

Because this is static frontend work inside a backend repo, testing will focus on:
- backend regression: run `backend/mvnw.cmd test`
- static asset verification: ensure files are served from Spring Boot static resources
- manual smoke test checklist:
  - open page in browser
  - register and login
  - call health check
  - upload a sample text file
  - query material list
  - create QA session
  - ask one question
  - generate one review outline

## File Plan

Files to create:
- `backend/src/main/resources/static/index.html`
- `backend/src/main/resources/static/styles.css`
- `backend/src/main/resources/static/app.js`

Files to update:
- `backend/README.md`

## Risks and Mitigations

### Risk: One page becomes too crowded

Mitigation:
- separate into section cards
- use compact forms
- add small helper text and defaults

### Risk: Protected endpoints fail without obvious cause

Mitigation:
- display token state in authentication section
- show request log with response code and message

### Risk: Chunk upload interaction is awkward in plain JavaScript

Mitigation:
- keep the UI minimal
- support manual chunk flow testing instead of full automatic resumable uploader behavior

## Acceptance Criteria

The feature is complete when:
- the backend serves a single static page successfully
- the page can authenticate and persist JWT locally
- the page can exercise the main backend capabilities from one browser view
- request results are visible without opening devtools
- backend tests still pass
