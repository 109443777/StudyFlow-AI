# StudyFlow Frontend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a maintainable Vue front end for testing and demonstrating the StudyFlow AI backend end-to-end.

**Architecture:** Create an independent `frontend/` Vite application. Keep API access in `src/api`, shared DTO/VO contracts in `src/types`, authentication state in Pinia, and user-facing pages in focused Vue views.

**Tech Stack:** Vue 3, Vite, TypeScript, Pinia, Vue Router, Axios, Element Plus, Vitest.

---

### Task 1: Frontend Project Skeleton

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/index.html`
- Create: `frontend/vite.config.ts`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.node.json`
- Create: `frontend/.env.development`
- Create: `frontend/src/main.ts`
- Create: `frontend/src/App.vue`
- Create: `frontend/src/styles/theme.css`

- [ ] Create the Vite Vue application shell with strict TypeScript and a local API base URL.
- [ ] Install dependencies with `npm.cmd install`.
- [ ] Run `npm.cmd run typecheck` and expect no TypeScript errors.

### Task 2: API Contract and Robust Request Layer

**Files:**
- Create: `frontend/src/types/api.ts`
- Create: `frontend/src/utils/result.ts`
- Create: `frontend/src/utils/result.test.ts`
- Create: `frontend/src/api/request.ts`
- Create: `frontend/src/api/studyflow.ts`

- [ ] Define StudyFlow `Result<T>` and backend VO/DTO types with all IDs as `string`.
- [ ] Write tests for result unwrapping, backend error mapping, and unknown fallback errors.
- [ ] Implement Axios interceptors for JWT, timeout, backend `Result<T>`, and Chinese error messages.
- [ ] Implement API functions for auth, health, materials, parse tasks, summaries, contents, QA, and study plans.

### Task 3: Routing, Auth, and Shared Components

**Files:**
- Create: `frontend/src/router/index.ts`
- Create: `frontend/src/stores/auth.ts`
- Create: `frontend/src/components/AppShell.vue`
- Create: `frontend/src/components/StatusBadge.vue`
- Create: `frontend/src/components/LoadingBlock.vue`

- [ ] Implement route guards so protected pages require a JWT token.
- [ ] Persist auth state in `localStorage` and clear it on unauthorized errors.
- [ ] Add reusable status and loading components for consistent empty/loading/failure states.

### Task 4: Core Pages

**Files:**
- Create: `frontend/src/views/LoginView.vue`
- Create: `frontend/src/views/DashboardView.vue`
- Create: `frontend/src/views/MaterialsView.vue`
- Create: `frontend/src/views/MaterialDetailView.vue`
- Create: `frontend/src/views/QaView.vue`
- Create: `frontend/src/views/StudyPlansView.vue`

- [ ] Build Chinese login/register forms.
- [ ] Build dashboard health cards and quick links.
- [ ] Build material upload, list, task polling, content summary, and detail views.
- [ ] Build RAG QA with session creation, answer rendering, and reference chunk display.
- [ ] Build study plan generation and history views.

### Task 5: Verification and Documentation

**Files:**
- Modify: `backend/README.md`

- [ ] Run `npm.cmd run test`.
- [ ] Run `npm.cmd run typecheck`.
- [ ] Run `npm.cmd run build`.
- [ ] Run backend `mvn test`.
- [ ] Add frontend startup steps to the README.
- [ ] Commit and push the completed frontend.
