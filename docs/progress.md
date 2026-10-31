# Progress

## Current Status

M1 complete: Flyway V1 schema, entities for Brand, Location, Campaign and AdVariant, CRUD endpoints for brands, locations and campaigns, Bean Validation, and ProblemDetail error handling. `./mvnw verify` passes (66 tests). Next: M2 (Claude API client).

## Milestones

- [x] M0: Skeleton, CI, docs scaffolding
- [x] M1: Domain model and CRUD
- [ ] M2: Claude API client
- [ ] M3: Validator and generation endpoint
- [ ] M4: Approval workflow and end-to-end test
- [ ] M5: Packaging, API docs, README
- [ ] M6: AWS deployment

## Session Log

### 2026-10-31: M0

- Generated the project from Spring Initializr (curl): Boot 4.1.2, Java 21, Maven wrapper; deps webmvc, data-jpa, postgresql, flyway, validation, actuator, testcontainers.
- Added `.gitignore`, `.env.example`, `docker-compose.yml` (postgres:16, healthcheck, named volume), and `application.yml` (env-var datasource, `ddl-auto: validate`, `open-in-view: false`, Flyway on `classpath:db/migration`, actuator exposes health only).
- Tests: context-loads and `GET /actuator/health` → 200/UP, both on a Testcontainers `postgres:16` with `@ServiceConnection`. The health test uses Boot 4's `@AutoConfigureRestTestClient` + `RestTestClient`.
- CI workflow: Temurin 21, Maven cache, `./mvnw -B verify`.
- Verified: `./mvnw verify` (2 tests, 0 failures); `docker compose up -d` + `./mvnw spring-boot:run` + `curl /actuator/health` → `{"status":"UP"}`.
- Boot 4 differences found by inspecting the generated pom and resolved jars: renamed/split starters, per-technology `-test` starters, Testcontainers 2 artifact names and `org.testcontainers.postgresql.PostgreSQLContainer` (non-generic), `TestRestTemplate` moved to `spring-boot-resttestclient` and is no longer auto-configured, JUnit Jupiter 6.0.3, Jackson 3.

### 2026-10-31: M0 docs

- Replaced CLAUDE.md, docs/plan.md, docs/flow.md, and docs/decisions.md with the owner's drafts; README.md from the draft with the verified Running Locally section kept.
- Ran every CLAUDE.md command in PowerShell: `verify`, `test -Dtest=ClassName`, `docker compose up -d` / `down`, `spring-boot:run`, `spring-boot:test-run` (no compose), `curl` health. All work. `-Dtest` with a dotted value must be quoted in PowerShell, so that's now documented.
- Checked every class, annotation, package, and artifact named in the drafts against the resolved jars (Boot 4.1.2, Spring Framework 7.0.10, Testcontainers 2.0.5, Jackson 3.1.7, JUnit 6.0.3). All exist. Added exact packages for the moved test classes and clarified that Jackson annotations stay in `com.fasterxml.jackson.annotation`.
- Restored facts from the M0 ADRs that the draft decisions dropped. Fixed two draft errors (see log below).
- Checked: Boot 3.5 OSS end date 2026-06-30 (endoflife.date).
- Config: `spring.config.import: optional:file:.env[.properties]` (ADR-014). Verified against docker-compose Postgres by checking `pg_stat_activity`:
  - With a `.env` naming a test role, the app connected as that role.
  - With the same `.env` plus real `DB_*` env vars, it connected as the env-var role, so env vars win.
  - With no `.env`, it started on the defaults.
  - `./mvnw test` passed even with a `.env` pointing at a nonexistent host, because `@ServiceConnection` overrides the datasource.
  - `.env` is still git-ignored (`.gitignore:8`).

### 2026-10-31: M1

- Chores: README roadmap names now match plan.md. Mockito loads as a `-javaagent` via `maven-dependency-plugin:properties` + surefire `argLine`, following the Mockito 5.23.0 javadoc (section 0.3, read from the sources jar). The 4 self-attach / dynamic-agent warning lines are gone; `-X` confirms the forked JVM gets `-javaagent:...mockito-core-5.23.0.jar`.
- Spike, run in a throwaway copy before planning:
  - Hibernate 7.4.13 maps `List<String>` to `text[]` with or without `@JdbcTypeCode(SqlTypes.ARRAY)`; an empty list is stored as `'{}'`.
  - `validate` does not check array column types (ADR-016).
  - `@DataJpaTest` uses the Testcontainers Postgres.
  - Postgres reports the unique index name as the constraint name.
- Commits, each with `verify` green:
  1. V1 schema and entities (7 tests)
  2. ProblemDetail error handling (18)
  3. Brand CRUD (37)
  4. Location CRUD (51)
  5. Campaign CRUD (66)
  6. These docs
- Owner decisions: the field sizes as proposed; banned words de-duplicated ignoring case after trimming; error handling as the second commit; nested list endpoints 404 for an unknown brand.
- Ran every curl example from the M1 report in PowerShell against `spring-boot:run` + docker-compose Postgres: all 201/200 paths, 400 (field list), 404 (unknown brand on nested create), 409 (`acme fitness` after `Acme Fitness`), 400 unreadable body, 405.
- ADRs 015 to 018 added. flow.md error mapping now lists the framework errors and the 400 `errors` shape.
- Not done (out of M1 scope): update/delete endpoints, pagination, variant endpoints.

## Agent Mistakes Log

| Date | What the agent got wrong | How it was caught | Fix |
|------|--------------------------|-------------------|-----|
| 2026-10-31 | Assumed `./mvnw dependency:tree` downloads jars, then tried to inspect jars that were never downloaded | `unzip` failed: jar files not found in `~/.m2` | Ran `./mvnw dependency:build-classpath -Dmdep.includeScope=test` to resolve the jars, then inspected them |
| 2026-10-31 | Tried to stop the app by looking up the port-8080 owner with `Get-NetTCPConnection`, which returned nothing. The script still printed "port 8080 free" even though the app was running | Re-ran `curl /actuator/health` and still got HTTP 200 | Found the app's `java.exe` via `Win32_Process` (PID matched the startup log), stopped it, and confirmed the health endpoint no longer responded |
| 2026-10-31 | From the owner's drafts, not the agent: ADR-004 said `.gitattributes` forces LF for "mvnw and shell scripts". It only has `/mvnw text eol=lf` and `*.cmd text eol=crlf`; there's no shell-script rule | Compared the draft ADR against the committed `.gitattributes` | ADR-004 now describes the actual rules and says to add an LF rule when shell scripts are added |
| 2026-10-31 | From the owner's drafts, not the agent: the generate-flow sequence diagram in `flow.md` ended every path with `201 variant`, including the "malformed again → 502, nothing saved" branch, which contradicted step 7 | Read the diagram against the step list while reviewing the draft | Moved the 201 response into each branch that saves; the malformed-twice branch ends at 502 only |
| 2026-10-31 | Committed the README roadmap chore (`6d5de39`) without running `verify` first, which breaks the "verify before every commit" rule. The change was docs-only and the code was identical to the last green tree | Own re-check while writing the M1 session log | The next commit (`b9576e2`) ran a full green `verify` on a tree that includes it. From now on: run `verify` before every commit, docs-only included |
| 2026-10-31 | In the spike, imported `TestcontainersConfiguration` from a sub-package, assuming it was accessible. Initializr generates it package-private | Compiler: `TestcontainersConfiguration is not public in com.franchiseads` | Made it `public` (in the spike, then in the V1 commit) so feature-package tests can `@Import` it |
| 2026-10-31 | Wrote "ADR-014" in the V1 migration comment for the unique-index ADR, but ADR-014 already exists (`.env` loading) | Own re-check before running anything | Changed the reference to ADR-015 before the commit |
