# Plan

FranchiseAds generates localized Google Ads copy for franchise locations with the Claude API, validates every variant deterministically, and routes valid copy through an approval workflow. Built milestone by milestone; status lives in `progress.md`.

Each milestone ends with `verify` green, docs updated, and small commits.

---

## M0: Skeleton, CI, docs scaffolding (done)

Spring Boot 4.1 project from Initializr, docker-compose Postgres, Testcontainers context and health tests, GitHub Actions CI, docs scaffolding.

---

## M1: Domain model and CRUD (done)

**Goal:** the API stores brands, locations, and campaigns, with real validation and consistent errors. No AI yet.

**Scope**
- Flyway `V1__init.sql` creating `brand`, `location`, `campaign`, `ad_variant`.
  - `brand`: id, name (unique), voice_guidelines, banned_words `text[]`, created_at
  - `location`: id, brand_id FK, name, city, region, local_offer, created_at
  - `campaign`: id, brand_id FK, name, theme, created_at
  - `ad_variant`: id, campaign_id FK, location_id FK, headlines `text[]`, descriptions `text[]`, status, validation_passed, violations `text[]`, generation_attempts, rejection_reason, model, version, created_at, updated_at
- JPA entities and repositories for all four. `AdVariant` is mapped now; its endpoints come in M3.
- Endpoints:
  - `POST /brands`, `GET /brands`, `GET /brands/{id}`
  - `POST /brands/{brandId}/locations`, `GET /brands/{brandId}/locations`, `GET /locations/{id}`
  - `POST /brands/{brandId}/campaigns`, `GET /brands/{brandId}/campaigns`, `GET /campaigns/{id}`
- Request DTOs with Bean Validation (not blank, max lengths, banned words not blank).
- Global error handler returning `ProblemDetail`: 400 with field errors, 404, 409 for duplicate brand name.

**Tests**
- `@WebMvcTest` per controller: happy path, validation failure, not found.
- One Testcontainers persistence test proving Flyway V1 applies, `ddl-auto: validate` passes, and `text[]` columns round-trip as `List<String>`.

**Acceptance criteria**
- Brand, location, and campaign can be created and read with curl.
- Invalid input returns 400 `ProblemDetail` listing each bad field.
- Unknown IDs return 404. Duplicate brand name returns 409.
- `verify` green locally and in CI.

**Out of scope:** update/delete endpoints, pagination, variant endpoints, anything Claude.

---

## M2: Claude API client (done)

**Goal:** a reliable, fully tested client for the Claude Messages API.

**Scope**
- `ClaudeProperties` (`@ConfigurationProperties`): base URL (default `https://api.anthropic.com`), API key (`ANTHROPIC_API_KEY`), model (`ANTHROPIC_MODEL`, default `claude-haiku-5-5`), max tokens, connect timeout, read timeout, max attempts.
- `ClaudeClient` on Spring's `RestClient`: `POST /v1/messages` with `x-api-key` and `anthropic-version: 2026-10-21` (ADR-025). Returns the text content.
- Retry policy from `flow.md`: up to 3 attempts, exponential backoff with jitter, honors `retry-after` (capped), retries 429, 5xx, and timeouts only. Hand-written, with an injectable sleeper.
- Typed failures: `ClaudeUnavailableException`, `ClaudeTimeoutException`, `ClaudeRequestException` (non-retryable 4xx).
- `PromptBuilder`: system and user prompts from brand, location, and campaign. Asks for JSON only. Has a regeneration variant that includes the previous output and its violations.
- `AdCopyParser`: model text → `AdCopy(headlines, descriptions)`. Tolerates markdown fences, trims entries, rejects anything else with `MalformedAdCopyException`.
- Add WireMock (ask before adding; confirm a Boot 4 compatible artifact). Chosen: `org.wiremock:wiremock-standalone` 3.13.2, test scope (ADR-019).

**Tests**
- WireMock: 200 valid, 200 with malformed body, 429 then 200, 429 with `retry-after`, 500 three times (gives up), 529 then 200, 400 (no retry, single request), 401 (no retry), read timeout.
- Assert request count and headers in WireMock, not just the result.
- Unit tests for `PromptBuilder` and `AdCopyParser` (fences, extra prose, missing fields, wrong types, empty arrays).

**Acceptance criteria**
- Every scenario above passes, and the test suite doesn't actually sleep.
- No test touches the real API. CI stays green with no key.
- One optional manual smoke test against the real API, documented in the README, never in CI.

**Out of scope:** validator, saving variants, endpoints.

---

## M3: Validator and generation endpoint (done)

**Goal:** the core feature. Generate, validate, regenerate once, save.

**Scope**
- `AdCopyValidator` (pure, no Spring) with these rules:
  - exactly the requested count: 5 headlines, 2 descriptions (configurable, within Google's limits of 3 to 15 headlines and 2 to 4 descriptions)
  - headline at most 30 characters, description at most 90, counted as Unicode code points
  - no blank entries
  - no exclamation marks in headlines (Google Ads editorial rule)
  - no banned brand words or phrases (case-insensitive, whole word)
  - no duplicates within headlines or within descriptions (case-insensitive, trimmed)
  - each violation names the rule, the field, the index, and the actual value
- `GenerationService` implementing the flow in `flow.md`, including the transaction boundaries.
- Per-request deadline (added during M3, ADR-027): read timeout 20s and a 50s deadline per generate request, shared by both model calls and their retries. Each attempt's read timeout is clamped to the time left; no attempt or retry delay starts that can't finish in time (504). Measured with an injectable `Clock`.
- Last parseable attempt (added during M3, ADR-030): if any call produced parseable copy, save the copy from the last call that did, flagged with its violations if it has any. 502/503/504 only when no call produced parseable copy. `generationAttempts` counts the model calls that returned a response.
- Requests send `output_config.effort` (default `low`, ADR-026).
- Endpoints:
  - `POST /campaigns/{campaignId}/generate` with `{ "locationId": ... }` → 201 with the variant
  - `GET /campaigns/{campaignId}/variants?status=...`
  - `GET /variants/{id}`

**Tests**
- Validator: one test per rule, boundaries (30 vs 31, 90 vs 91), emoji counted as one character, banned word as a substring of a longer word must NOT match.
- `GenerationService` with Mockito: valid first try (1 call), invalid then valid (2 calls, second prompt contains the violations), invalid twice (saved flagged), malformed then valid, malformed twice (502, nothing saved), Claude unavailable on the first call (503, nothing saved), location from another brand (422). Added: invalid then malformed, and invalid then a failed regeneration call (first attempt saved flagged); malformed then a failed call (that error, nothing saved).
- Deadline: each attempt's read timeout clamped to the time left, proven over HTTP with WireMock; no attempt after the deadline; no retry delay that passes it.
- Transaction boundaries: no transaction active while the Claude client is called (Spring context + Postgres).
- Controller tests for the three endpoints.

**Acceptance criteria**
- Generation never makes more than 2 model calls per request.
- A flagged variant is saved with every violation listed and `validationPassed: false`.
- Nothing is saved when no parseable copy was produced.
- The Claude phase of a generate request ends within 50s of its start.

**Out of scope:** approval actions, editing variants by hand.

---

## M4: Approval workflow and end-to-end test (done)

**Goal:** variants move through review safely, and the whole system is proven against real Postgres.

**Scope**
- Test isolation first (added during M4, ADR-031): `TruncateTablesExtension` truncates every application table except `flyway_schema_history`, with `RESTART IDENTITY CASCADE`, before each test that writes through a full context.
- `VariantStatus.next(action, validationPassed)` owns every transition rule, the flagged-submit guard included (ADR-032). `VariantAction` is `SUBMIT`, `APPROVE`, `REJECT`. Repeating an action is 409, not idempotent.
- `POST /variants/{id}/submit`, `POST /variants/{id}/approve`, `POST /variants/{id}/reject` with a required `reason` (stripped, not blank, at most 500). Each returns 200 with the updated variant; `VariantResponse` gained `rejectionReason` and `updatedAt`.
- Guards: only `validationPassed` variants can be submitted. Illegal transitions return 409 with `currentStatus` and `attemptedAction` extension properties and a readable detail.
- `@Version` optimistic locking (ADR-033): a stale update maps `ObjectOptimisticLockingFailureException` to 409 "The variant was modified concurrently. Reload it and retry." ETag/If-Match considered and rejected.
- One Testcontainers end-to-end test with WireMock standing in for Claude and the real `ClaudeClient` pointed at it: create brand, location, campaign, generate, submit, approve. Plus: a flagged variant (2 calls, both violating) cannot be submitted, and a rejected variant cannot be approved.

**Tests**
- Unit tests covering every status × action × validation-flag combination (24), allowed and rejected, plus a loop that fails if anything else becomes allowed.
- Controller tests for each action, the reason rules, 404 and both kinds of 409.
- Concurrency test for the optimistic lock, deterministic: the reject request loads the variant, a separate transaction approves it and commits, the stale reject gets 409 and doesn't land. Shown to fail with the `@Version` check turned off.

**Acceptance criteria**
- Every illegal transition returns 409, never 500.
- The end-to-end test passes locally and in CI.

**Out of scope:** reopening rejected variants, roles and permissions.

---

## M5: Packaging, API docs, README (done)

**Goal:** anyone can clone, run, and understand the project in five minutes.

**Scope**
- Multi-stage `Dockerfile` (ADR-034):
  - build on `eclipse-temurin:21.0.13_9-jdk-noble` with the Maven wrapper, run on `…-jre-noble`
  - Maven dependencies through a BuildKit cache mount
  - Boot 4.1's `-Djarmode=tools extract --layers`
  - tests skipped in the image build (CI runs them; Testcontainers can't run in `docker build`)
  - non-root user `app`, port 8080, heap from the container limit via `JAVA_TOOL_OPTIONS`
  - `.dockerignore` is an allowlist
- docker-compose `app` service under the `app` profile (ADR-035): waits for a healthy Postgres, `DB_URL` fixed to the `postgres` service name, `ANTHROPIC_API_KEY` passed through from the shell or `.env` at `up` time. Plain `up -d` still starts only Postgres; teardown is `--profile app down`.
- springdoc-openapi 3.1.1 with Swagger UI (ADR-036; 3.2.0 was too new to adopt):
  - every endpoint has a summary, its success response, and its `ProblemDetail` errors from flow.md, via `@ProblemResponses` and one shared response per status
  - `API_DOCS_ENABLED` (default `true`) switches the docs off
- CI: a second job builds the Docker image (no push).
- README rewritten: overview, Mermaid architecture and state diagrams, API table, three ways to run (PowerShell and Linux), configuration tables, testing strategy, design decisions, measured results, Built with Claude Code, roadmap.

**Tests**
- `OpenApiDocumentationTests`:
  - the document has exactly the operations Spring MVC maps, read from `RequestMappingHandlerMapping`
  - each has a summary and one success response (201 for creates) and lists exactly its flow.md error statuses
  - all errors reference the shared definitions
  - Swagger UI is served
- `ApiDocsDisabledTests`: all three docs paths are 404 when switched off.
- Each check was shown to fail on a deliberate break.

**Acceptance criteria**
- `docker build` and `docker compose --profile app up` work from a clean clone.
- Swagger UI lists every endpoint with request and response schemas.
- Following the README from scratch works on a clean machine.

Met. The README was followed exactly as written in a fresh clone on Windows (CRLF checkout), on port 8080: full stack, host run, one test class and `verify`.

---

## UI: local review console (separate repo, Lovable) (in progress)

**Goal:** a reviewer can work through the approval workflow in a browser instead of curl or Swagger UI.

**Scope**
- The UI lives in its own repo, built with Lovable, and runs locally on the Vite dev server (`http://localhost:5173`). This repo doesn't build, serve or test it.
- This repo's only change: CORS for that origin (ADR-037). `CORS_ALLOWED_ORIGINS` defaults to `http://localhost:5173,http://127.0.0.1:5173`; `GET`, `POST`, `OPTIONS`; `Content-Type`; `Location` exposed; no credentials. Done.
- The UI uses the API as it is: brands, locations, campaigns, generate, the variant list and the review actions. The contract is the OpenAPI document at `/v3/api-docs`.

**Out of scope**
- Auth, sessions or cookies (the API has none, so CORS sends no credentials).
- Hosting the UI. A hosted UI's origin is an M6 question.
- API changes for the UI's convenience. If the UI needs one, it gets proposed and planned first, like any other change.

**Acceptance criteria**
- With the API on 8080 and the UI on 5173, the UI can create a brand, location and campaign, generate a variant, submit it, and approve or reject it, and the browser console shows no CORS errors.
- The UI shows `ProblemDetail` errors (400, 404, 409, 422, 502–504), and reads created resources' URLs from `Location`.

---

## M6: AWS deployment

**Goal:** a public URL running the service.

**Decide at the start of M6** (record as an ADR):
- Option A: ECS Fargate + RDS Postgres + ALB, image in ECR, API key in Secrets Manager.
- Option B: one EC2 instance running the compose stack, key in SSM Parameter Store. Cheaper, less production-like.

**Either way**
- Flyway migrates on startup. Load balancer or monitoring uses `/actuator/health`.
- The API key is never baked into the image or stored in plain text in task definitions or user data.
- Tear down or scale to zero after demos. ALB and RDS bill by the hour.

**Acceptance criteria**
- `/actuator/health` and Swagger UI reachable at a public URL.
- Deployment steps documented in the README well enough to repeat.
