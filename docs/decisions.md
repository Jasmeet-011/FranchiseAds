# Decisions

Short ADRs. Each has context, the decision, and its consequences. Newest at the bottom. Superseded decisions stay, marked as such.

---

## ADR-001: Spring Boot 4.1 over 3.5

**Context:** Spring Boot 3.5, the last 3.x line, left open-source support on June 30, 2026. 4.1 is the current GA line.
**Decision:** Spring Boot 4.1.x on Java 21 (4.1.2 at creation), generated from Spring Initializr.
**Consequences:** Boot 4 moved to Jackson 3, reorganized starters (e.g. `spring-boot-starter-webmvc`) and split test support into per-technology `-test` starters, and pulls in JUnit 6 and Testcontainers 2. Most tutorials and agent training data are Boot 3, so every import is checked against the resolved dependencies instead of written from memory.

## ADR-002: Flyway owns the schema, Hibernate only validates

**Context:** `ddl-auto: update` silently mutates the schema, isn't reviewed or reproducible across environments, and can't express renames or data migrations.
**Decision:** All schema changes are versioned Flyway migrations in `src/main/resources/db/migration`. `spring.jpa.hibernate.ddl-auto: validate`, so Hibernate never changes the schema.
**Consequences:** Every entity change needs a migration. Entity and migration drift fails at startup instead of in production. Committed migrations are never edited.

## ADR-003: Testcontainers Postgres instead of H2

**Context:** H2 differs from Postgres in SQL dialect, types, and constraint behaviour, and this schema uses Postgres arrays.
**Decision:** Tests that touch the database use a real `postgres:16` through Testcontainers, wired in with `@ServiceConnection`.
**Consequences:** Docker is required to run tests (available locally and on GitHub's ubuntu runners). Tests exercise the same database as production and run the real Flyway migrations. The first run is slower while the image is pulled.

## ADR-004: GitHub Actions CI, Windows-safe repo settings

**Context:** Development happens on Windows; CI runs on Linux.
**Decision:** CI runs `./mvnw -B verify` on Temurin 21 with a Maven cache, on every push and pull request to main. `.gitattributes` forces LF for `mvnw` (and CRLF for `*.cmd`), and `mvnw` is committed as executable (100755). There are no other shell scripts yet; add an LF rule for them when there are.
**Consequences:** Without these, CI fails on CRLF line endings or "Permission denied" before any test runs.

## ADR-005: Package by feature

**Context:** Layer packages (`controller`, `service`, `repository`) scatter one feature across the codebase.
**Decision:** One package per feature (`brand`, `location`, `campaign`, `variant`, `generation`, `claude`, `validation`) plus `common`.
**Consequences:** A feature's code sits together, and package-private visibility can hide internals.

## ADR-006: Records for DTOs, no Lombok or MapStruct

**Context:** Both libraries add annotation processing and hide code that's short to write by hand.
**Decision:** Request and response DTOs are Java records. Mapping is explicit `from(...)` methods. Entities are plain classes.
**Consequences:** Slightly more code, nothing hidden, no generated-code surprises.

## ADR-007: String lists stored as Postgres `text[]`

**Context:** Banned words, headlines, descriptions, and violations are always read and written whole, never queried element by element.
**Decision:** Map them as `List<String>` to `text[]` columns via Hibernate's array support.
**Consequences:** No join tables and no JSON mapper dependency. If per-element querying is ever needed, migrate to a child table.

## ADR-008: Call the Claude API with `RestClient`, not the SDK

**Context:** The official Anthropic Java SDK exists, but this service's value is in controlling timeouts, retries, and failure handling.
**Decision:** A small `ClaudeClient` on Spring's `RestClient` against `POST /v1/messages`.
**Consequences:** Full control over the retry policy and error mapping, and WireMock tests at the HTTP level. We maintain request and response mapping ourselves.

## ADR-009: Hand-written retry with an injectable sleeper

**Context:** Retry rules are specific (429, 5xx, 529, timeouts; never other 4xx; honor `retry-after`) and must be tested without real waiting.
**Decision:** A short explicit retry loop inside `ClaudeClient`, with backoff delays going through an injectable sleeper.
**Consequences:** The whole policy is readable in one place and tests run instantly. Trade-off: no library features like circuit breaking, which this service doesn't need.

## ADR-010: The validator is the source of truth, not the model

**Context:** Models ignore character limits and instructions some of the time, no matter how the prompt is written.
**Decision:** Every output goes through a deterministic `AdCopyValidator`. The prompt states the rules, but only the validator decides.
**Consequences:** Invalid copy can't reach review. Validation is testable without any AI.

## ADR-011: Regenerate once, then flag

**Context:** Looping until the model passes has unbounded cost and latency, and some briefs (a long brand name, say) can't fit 30 characters at all.
**Decision:** At most one regeneration, with the violations fed back. If it still fails, save the variant flagged with its violations so a human can see why.
**Consequences:** At most 2 model calls per request. Some variants end up flagged, which is the honest outcome.

## ADR-012: No database transaction across the Claude call

**Context:** A model call takes seconds. A transaction held across it pins a pooled connection the whole time.
**Decision:** Load inputs in a read-only transaction, call Claude with no transaction, save in a new short transaction.
**Consequences:** The connection pool survives slow upstream calls. Inputs could change between load and save, which is acceptable here.

## ADR-013: Count characters as Unicode code points

**Context:** `String.length()` counts UTF-16 units, so an emoji counts as 2.
**Decision:** Length limits use `codePointCount`.
**Consequences:** Closer to how Google Ads counts. Google counts some wide characters (for example CJK) as 2, which this deliberately doesn't model.
