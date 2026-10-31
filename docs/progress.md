# Progress

## Current Status

M0 complete: skeleton, CI and docs scaffolding are in place. `./mvnw verify` passes, and the app runs against docker-compose Postgres with `/actuator/health` reporting `UP`.

## Milestones

- [x] M0: Skeleton, CI, docs scaffolding
- [ ] M1: Domain model + CRUD, Flyway V1, Bean Validation, global error handling
- [ ] M2: Claude API client
- [ ] M3: Ad validator + regenerate-once loop
- [ ] M4: Approval state machine + Testcontainers end-to-end test
- [ ] M5: Dockerfile, OpenAPI docs, README polish
- [ ] M6: AWS deployment

## Session Log

### 2026-10-31: M0

- Generated the project from Spring Initializr (curl): Boot 4.1.2, Java 21, Maven wrapper; deps webmvc, data-jpa, postgresql, flyway, validation, actuator, testcontainers.
- Added `.gitignore`, `.env.example`, `docker-compose.yml` (postgres:16, healthcheck, named volume), and `application.yml` (env-var datasource, `ddl-auto: validate`, `open-in-view: false`, Flyway on `classpath:db/migration`, actuator exposes health only).
- Tests: context-loads and `GET /actuator/health` → 200/UP, both on a Testcontainers `postgres:16` with `@ServiceConnection`. The health test uses Boot 4's `@AutoConfigureRestTestClient` + `RestTestClient`.
- CI workflow: Temurin 21, Maven cache, `./mvnw -B verify`.
- Verified: `./mvnw verify` (2 tests, 0 failures); `docker compose up -d` + `./mvnw spring-boot:run` + `curl /actuator/health` → `{"status":"UP"}`.
- Boot 4 differences found by inspecting the generated pom and resolved jars: renamed/split starters, per-technology `-test` starters, Testcontainers 2 artifact names and `org.testcontainers.postgresql.PostgreSQLContainer` (non-generic), `TestRestTemplate` moved to `spring-boot-resttestclient` and is no longer auto-configured, JUnit Jupiter 6.0.3, Jackson 3.

## Agent Mistakes Log

| Date | What the agent got wrong | How it was caught | Fix |
|------|--------------------------|-------------------|-----|
| 2026-10-31 | Assumed `./mvnw dependency:tree` downloads jars, then tried to inspect jars that were never downloaded | `unzip` failed: jar files not found in `~/.m2` | Ran `./mvnw dependency:build-classpath -Dmdep.includeScope=test` to resolve the jars, then inspected them |
| 2026-10-31 | Tried to stop the app by looking up the port-8080 owner with `Get-NetTCPConnection`, which returned nothing. The script still printed "port 8080 free" even though the app was running | Re-ran `curl /actuator/health` and still got HTTP 200 | Found the app's `java.exe` via `Win32_Process` (PID matched the startup log), stopped it, and confirmed the health endpoint no longer responded |
