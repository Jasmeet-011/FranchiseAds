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
**Decision:** CI runs `./mvnw -B verify` on Temurin 21 with a Maven cache, on every push and pull request to main. `.gitattributes` forces LF for `mvnw` (and CRLF for `*.cmd`), and `mvnw` is committed as executable (100755). There are no other shell scripts yet; add an LF rule for them when there are. *M5 note:* `scripts/check-image-secrets.sh` is the first, so `.gitattributes` now has `*.sh text eol=lf`, and the script is committed as executable.
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

## ADR-014: Load `.env` for local runs

**Decision:** `spring.config.import: optional:file:.env[.properties]`, so local runs read `./.env`; it's optional, so CI and AWS run without one, and real environment variables still override it.

## ADR-015: Case-insensitive brand names enforced by a unique index on `lower(name)`

**Context:** "Acme" and "acme" are the same brand to a human. An exists-then-insert check in the service races: two concurrent requests both see "not found" and both insert.
**Decision:** `create unique index uq_brand_name_lower on brand (lower(name))` in V1 is the only uniqueness check. The service calls `saveAndFlush` so a duplicate fails inside the request; `GlobalExceptionHandler` maps a `DataIntegrityViolationException` whose Hibernate `ConstraintViolationException` is `UNIQUE` on `uq_brand_name_lower` (Postgres reports the index name as the constraint name) to 409.
**Consequences:** Correct under concurrency, one source of truth. A failed insert still consumes an identity value, and Hibernate logs each duplicate at WARN (`HHH000247`). Other integrity violations are not mapped: request validation should prevent them, so they return a generic 500 and are logged at ERROR as bugs. A new unique constraint needs an entry in the handler's map to become a 409.

## ADR-016: `ddl-auto: validate` does not check array columns, so array mappings need round-trip tests

**Context:** Hibernate 7.4 maps `List<String>` to `text[]` with or without `@JdbcTypeCode(SqlTypes.ARRAY)`. A spike showed that `validate` accepts a `varchar` column for such a field (with or without the annotation): startup and inserts succeed, and only reads fail. It does reject e.g. `varchar` for an `int` field.
**Decision:** Keep `@JdbcTypeCode(SqlTypes.ARRAY)` to make the intent explicit, and require a Testcontainers round-trip test for every array column, including an empty list (`PersistenceMappingTests`).
**Consequences:** Array column changes are guarded by tests, not by startup validation. Adding an array column without a round-trip test is a review failure.

## ADR-017: Normalize request input before validation

**Context:** Without normalization, `" Acme"` would dodge the case-insensitive unique index, an empty optional field would be stored as `""` in one row and `null` in another, and duplicate banned words (`"Cheap"`, `"cheap"`) would be noise for the M3 validator and count against the list limit.
**Decision:** Request records normalize in their compact constructors, before Bean Validation runs: strings are stripped, blank optional fields become null, and banned words default to `[]` and are de-duplicated ignoring case (first spelling kept). Size limits apply to the normalized values.
**Consequences:** Stored data is clean and the 50-entry limit counts distinct words. A client sending `["Cheap", "cheap"]` gets back `["Cheap"]` without an error.

## ADR-018: Nested endpoints return 404 for an unknown parent

**Context:** `GET /brands/{brandId}/locations` for a brand that doesn't exist could return `[]`, which is indistinguishable from a real brand with no locations.
**Decision:** Every `/brands/{brandId}/...` endpoint (create and list) loads the brand first and returns 404 if it is missing.
**Consequences:** Clients can tell a typo from an empty list, at the cost of one extra primary-key lookup per list call.

## ADR-019: WireMock standalone (shaded) for HTTP-level Claude tests

**Context:** Boot 4.1.2 manages Jetty 12.1.14, Jackson 2.21.7 and httpclient5 5.6.5. The non-shaded `org.wiremock:wiremock-jetty12` 3.13.2, and `wiremock-spring-boot` 4.4.3 (built against Jetty 12.1.6), would have those versions forced onto them by Boot's dependency management. WireMock 4.x is still beta (4.0.0-beta.41).
**Decision:** `org.wiremock:wiremock-standalone` 3.13.2, test scope. It shades all of its dependencies; the only unshaded classes are four `org.jspecify` annotations, identical to the ones Spring 7 uses. Client tests are plain JUnit with `WireMockExtension` (works under JUnit 6.0.3) and construct `ClaudeClient` directly. No Spring context, no Spring integration library.
**Consequences:** No version conflicts with anything Boot manages. A 19.5 MB test jar. M4's end-to-end test will need its own wiring to point the app at WireMock (a dynamic `franchiseads.claude.base-url`).
*M4 note:* `ApprovalWorkflowEndToEndTests` does that wiring with a static `WireMockExtension` (dynamic port) and a `@DynamicPropertySource` that supplies `claudeApi::baseUrl` plus the API key, model, effort and max attempts. The supplier is only read when the Spring context starts, which happens after the extension has started the server. Still no Spring integration library.

## ADR-020: JDK `HttpClient` request factory; timeouts detected by `HttpTimeoutException`

**Context:** The client needs both a connect timeout and a read timeout. A spike showed what `RestClient` throws for each failure.
**Decision:** `JdkClientHttpRequestFactory` (in spring-web, no new dependency) over a `java.net.http.HttpClient` with `connectTimeout` and HTTP/1.1 pinned; the read timeout comes from `setReadTimeout`. A 300 ms read timeout against a 1500 ms WireMock delay throws `ResourceAccessException` caused by `java.net.http.HttpTimeoutException: Request cancelled`, after about 317 ms. Connection refused throws `ResourceAccessException` caused by `ConnectException`. A connect timeout is `HttpConnectTimeoutException`, a subclass of `HttpTimeoutException`. The client treats a `ResourceAccessException` with an `HttpTimeoutException` in its cause chain as a timeout, and any other one as a connection error.
**Consequences:** Swapping the request factory changes the exception types (for comparison, `SimpleClientHttpRequestFactory` throws `SocketTimeoutException: Read timed out`), so `ClaudeClientTests` pins the cause chain.

## ADR-021: A missing API key doesn't stop the app; the client refuses to call

**Context:** CI and fresh clones have no `ANTHROPIC_API_KEY`, and all CRUD endpoints work without one. Failing startup would break CI, local runs and the health check for no benefit.
**Decision:** `franchiseads.claude.api-key` defaults to empty. `ClaudeClient.complete` throws `ClaudeRequestException("API key not configured")` before building any request when the key is blank. `ClaudeProperties.toString()` masks the key.
**Consequences:** A misconfigured deployment starts and looks healthy until the first generation, which fails with a clear 502 (once M3 maps it). Acceptable for this project; a deployment check can look for the key separately.

## ADR-022: Retry transport failures only; the envelope vs. model-output boundary

**Context:** flow.md's original table didn't cover connection errors, `retry-after` on 5xx, 5xx codes outside the list, or a 200 whose body isn't a Messages response.
**Decision (owner-approved):** Retry 429, every 5xx (529 included), timeouts, connection errors (refused, reset), and a 2xx whose envelope is invalid (not JSON, no `content` array, a text block without a string). Honor a whole-seconds `retry-after` on any retryable response, capped at 10s and without jitter; anything else in that header falls back to the backoff. Every other 4xx fails immediately. The *envelope* is the transport's concern; the *model output* inside a valid envelope is not: garbage text, `max_tokens` and `refusal` are returned after one request, and `AdCopyParser` plus the single regeneration deal with them.
**Consequences:** Client retries never multiply model calls for bad output, so the "at most 2 model calls" rule in flow.md stays meaningful. The final exception type follows the last attempt (timeout means 504, anything else 503).

## ADR-023: Prompt-injection defense: tagged data sections with escaped angle brackets

**Context:** Voice guidelines, banned words, local offers and campaign themes are user-supplied, and the regeneration prompt also carries model output and violation text quoting it.
**Decision:** Each such value goes in its own tagged section (`<local_offer>…</local_offer>` etc.). The system prompt says tagged content is data and that instructions inside it must never be followed. `<` and `>` inside values are escaped to `&lt;`/`&gt;`, so a value can't close its own section and continue as instructions. Regeneration is one user message with the previous output inside `<previous_output>`, not a replayed assistant turn.
**Consequences:** This reduces injection risk; it doesn't remove it, which is one more reason the validator decides, not the model (ADR-010). Values containing angle brackets reach the model escaped, which is harmless for ad copy.

## ADR-024: `AdCopyRules` is the single source of counts and length limits

**Context:** The prompt has to state the same counts and limits the validator enforces, and they're configurable within Google's limits.
**Decision:** One `@ConfigurationProperties("franchiseads.ad-copy")` record, `AdCopyRules`: headline count 5 (3 to 15), description count 2 (2 to 4), headline max 30, description max 90, each bounded by Bean Validation. `PromptBuilder` interpolates them, and its tests use non-default rules to prove nothing is hard-coded. The M3 validator takes the same record.
**Consequences:** A configuration change can't make the prompt and the validator disagree. Out-of-range values fail startup.

## ADR-025: API version `2026-10-21`; no thinking or effort fields; `max_tokens` 4096

*Partially superseded by ADR-026: the request now sends `output_config.effort`. The API version, `max_tokens` and "no thinking field" parts stand.*

**Context:** The versioning docs list `2026-10-21` as current and recommend the latest. Its Messages changes (sampling parameters removed, document/citation placement) don't touch our request. The default model, `claude-haiku-5-5`, runs adaptive thinking by default at effort `medium`, and thinking tokens count against `max_tokens`.
**Decision (owner-approved):** Send `anthropic-version: 2026-10-21`. Send only `model`, `max_tokens`, `system` and one user message, with no `thinking`, `effort` or sampling fields, so the request stays valid if `ANTHROPIC_MODEL` points at another model. Default `max_tokens` to 4096 to leave room for thinking; a `max_tokens` stop is treated as malformed output.
**Consequences:** Thinking is billed as output. The live smoke test logs the token counts and whether thinking blocks came back, so the owner can decide whether to configure effort after a real run.

## ADR-026: Send `output_config.effort`, default `low`; never send `thinking`

**Context:** The M2 live run (ADR-025 request, no effort field) took 3632 ms, with 652 input and 624 output tokens and 1 thinking block. The JSON answer itself is roughly 120 tokens, so about 80% of the output was thinking, billed as output. The docs (platform.claude.com effort and thinking pages, read 2026-10-31) say:
- `claude-haiku-5-5` supports effort `low`, `medium`, `high`, `xhigh`, `max`; the default is `medium`. Effort goes in `output_config.effort`. At `low`, the model "skips thinking for simple tasks".
- Haiku 5.5 accepts `thinking: {"type": "disabled"}` only at effort `high` or below (400 at `xhigh`/`max`). Opus 5.5, Fable 5/5.1 and Sonnet 5.5 reject `disabled` at every level.
- Effort is not supported on Haiku 4.5 or Sonnet 4.5.
- The docs recommend lowering effort before disabling thinking.

**Decision (owner-approved):** `ClaudeProperties.effort` (`ANTHROPIC_EFFORT`, default `low`, bound ignoring case), sent as `"output_config": {"effort": "low"}`. `ANTHROPIC_EFFORT=none` leaves the field out, which is the escape hatch for models without effort. An empty value can't serve as that switch: Spring Boot binds it to null and then applies the `@DefaultValue`, so it means `low` (pinned by `ClaudePropertiesTests`). No `thinking` field is sent, so the request stays valid on every current model at every effort level. `ClaudeResponse` also carries `usage.output_tokens_details.thinking_tokens` (0 when absent), logged per call and in the live test, so the thinking share is measured instead of estimated.
**Consequences:** Less thinking and lower cost on the default model. `low` may still think on some requests; whether a run thought shows up in `thinking_tokens`. Pointing `ANTHROPIC_MODEL` at Haiku 4.5 or Sonnet 4.5 needs `ANTHROPIC_EFFORT=none` as well.

| Live run | Effort sent | Latency | Input tokens | Output tokens | Thinking tokens | Thinking blocks |
|---|---|---|---|---|---|---|
| M2 (before) | none (model default `medium`) | 3632 ms | 652 | 624 | not logged | 1 |
| After | `low` | 1817 ms | 652 | 133 | 0 | 0 |

Same brief and prompt in both runs. Output tokens fell from 624 to 133 (−79%), and none of the 133 went to thinking. The answer was valid in both (after: every headline ≤ 24 characters, descriptions under 80, no `!`). Each row is a single run, so the latency figures are approximate; the token counts are the reliable comparison.

## ADR-027: Read timeout 20s and a 50s per-request deadline

**Context:** With the M2 settings (3 attempts, read timeout 30s, `retry-after` capped at 10s) one model call could take 3 × 30 + 2 × 10 = 110s. Spring 7.0's `JdkClientHttpRequest` times the whole exchange, and the JDK starts its timer before connecting (`MultiExchange.responseAsyncImpl`), so the connect phase is inside the read timeout and adds nothing. A generate request makes up to two calls, and the regeneration only starts after call 1 returns. In the worst case call 1 succeeds on its third slow attempt with unusable output, and call 2 then fails the same way: **220s**. That is far above the 60s default idle timeout of the AWS load balancer planned for M6, which would cut the connection and return its own error page instead of our ProblemDetail. Live calls take about 2–4s (ADR-026).
**Decision (owner-approved):** Read timeout default 20s. The generate flow gets a deadline of 50s from the start of the request (`franchiseads.generation.deadline`), shared by both model calls and passed to `ClaudeClient.complete(system, user, deadline)`:
- Each attempt's read timeout is clamped to the time left before the deadline.
- No attempt starts after the deadline. A retry delay that would end at or after it isn't waited out.
- Either stop throws `ClaudeTimeoutException` (504), even when the last failure was a 429 or 5xx: the request ran out of time.
- The deadline is measured with an injectable `Clock`.
- The two-argument `complete` has no deadline and behaves as before.

Spring 7.0.10's `JdkClientHttpRequestFactory` only has a factory-level `setReadTimeout`. So a clamped attempt uses a throwaway factory around the one shared `java.net.http.HttpClient` (same connection pool, same connect timeout) through `restClient.mutate()`. Unclamped attempts reuse the normal `RestClient`. Rejected: a custom `ClientHttpRequestFactory`, which can't reach `JdkClientHttpRequest`'s timeout, and wrapping the call in `Future.get(timeout)`, which would leave the HTTP exchange running after we give up.
**Consequences:** The Claude phase of a generate request ends by start + 50s; loading and saving add milliseconds. Inside the budget, call 1 fits at most two full 20s attempts and a clamped third, and the regeneration gets what's left. A slow upstream gives fewer retries, not a longer request. `ClaudeClientTests` proves the clamp over HTTP: WireMock would answer 200 after 1s, inside a 5s read timeout, but with 150ms left before the deadline the attempt times out in under 900ms.

## ADR-028: Banned words match whole words, case-insensitively, across any whitespace

**Context:** plan.md says "case-insensitive, whole word". Java's `\b` follows its own word-character rules, which aren't Unicode-aware by default, and it misbehaves at phrase edges that aren't letters (`50% off`, `#1`). The owner's test cases: "cheap" must not match "cheapest", and "best in town" must match as a phrase.
**Decision:** Each banned entry is stripped and split on whitespace. Each token goes through `Pattern.quote`, the tokens are joined with `\s+`, and the result is wrapped in `(?<![\p{L}\p{N}])…(?![\p{L}\p{N}])` with `CASE_INSENSITIVE | UNICODE_CASE`. A match therefore can't touch a letter or digit on either side. One violation per banned word per entry, naming the brand's spelling of the word.
**Consequences:**
- "cheap" doesn't match "cheapest" or "overcheap".
- It does match "Cheap", "CHEAP", "cheap." and "cheap-looking": a hyphen is a boundary, which is the stricter choice for a banned list.
- "best in town" matches "Best In Town" and "best  in town", but not "best inn town".
- "café" matches "CAFÉ". Regex characters in banned words are literal.
- Not handled: a curly apostrophe doesn't match a straight one, and non-ASCII whitespace inside a phrase isn't treated as a space. Covered by `AdCopyValidatorTests`.

## ADR-029: Transaction boundaries through separate beans, proven by a test

**Context:** ADR-012 requires a read-only transaction to load, none during the Claude calls, and a new one to save. Spring's transactions are proxy-based: a `@Transactional` method called on `this` from `generate` would run without a transaction and nothing would say so.
**Decision:** `GenerationService.generate` has no `@Transactional`. Loading is `GenerationInputLoader.load` (`@Transactional(readOnly = true)`), which copies everything the prompt needs into plain values (`GenerationInputs`, `AdBrief`) while the session is open. Saving is `VariantService.saveGenerated` (`@Transactional`), which references the campaign and location with `EntityManager.getReference` instead of reloading them. Both are other beans, so calls go through their proxies. `TransactionTemplate` would also work, but it puts transaction plumbing inside the orchestration code, and each feature already owns its own transactional service.
**Consequences:** `GenerationTransactionBoundaryTests` runs the real context on Postgres with a mocked `ClaudeClient` that records `TransactionSynchronizationManager.isActualTransactionActive()` and `isSynchronizationActive()` at each call. It asserts `false` on a one-call and a two-call path, checks the variant was saved, and has a control test showing the probe does see an open transaction. A `@Transactional` added to `generate` fails it. As ADR-012 accepts, inputs could change between load and save.

## ADR-030: Save the last parseable attempt; fail only when there is none

**Context:** flow.md originally said "malformed again → 502, nothing saved" and left open what happens when the regeneration call itself fails (a timeout, the deadline, a 503). In both cases a parseable first attempt may already exist. Throwing it away costs a model call the client has to repeat, and hides output a human could review.
**Decision (owner-approved):** If any call produced parseable copy, the request saves a variant from the last call that did, flagged with that call's violations if it has any. 502/503/504 only when no call produced parseable copy. This covers every way the regeneration can fail: malformed output, `ClaudeTimeoutException` (including the deadline), `ClaudeUnavailableException` and `ClaudeRequestException`. `generationAttempts` counts the model calls that returned a response, so invalid → malformed is 2 and invalid → timeout is 1.
**Consequences:** A request never returns an error while holding usable output. The client can tell a "regeneration didn't complete" variant by `validationPassed=false` together with `generationAttempts=1`. Because `GenerationService` handles the regeneration failure instead of letting `GlobalExceptionHandler` see it, it logs the failure itself (ERROR for a rejected request, WARN otherwise), so nothing is swallowed. The full outcome table is in flow.md, step 7.

## ADR-031: Integration tests truncate every application table before each test

**Context:** Tests that write through a full Spring context share that context's database, so rows persisted across test classes and tests relied on names nobody else used. That worked until a test counted rows or expected a particular ID. Each distinct context configuration (one with `@MockitoBean ClaudeClient`, say) starts its own Postgres container, so the leaking happened within a context, not across all of them.
**Decision:** `TruncateTablesExtension`, a JUnit `BeforeEachCallback` in the test root package. It gets the context's `DataSource` through `SpringExtension.getApplicationContext`, lists the tables in `pg_tables` for the current schema except `flyway_schema_history`, and runs a single `truncate table … restart identity cascade`. Every class that writes through a full context declares `@ExtendWith(TruncateTablesExtension.class)`. It truncates before each test rather than after, so a test that crashed halfway can't leave rows for the next one. Not applied to `@DataJpaTest` classes (each test already rolls back) or to tests that don't write. Rejected alternatives: `@Sql` with a fixed truncate script, which has to be edited for every new table; `@Transactional` tests, which can't cover HTTP requests on another thread and would hide the commit-time behavior the generation and locking tests check; `@DirtiesContext`, which starts a new context and container per class.
**Consequences:** Each test starts with empty tables and IDs from 1. `TruncateTablesExtensionTests` proves both and that Flyway's history survives. A table added by a later migration is covered with no change. The unique names already in tests stay; they're harmless. Tests must not run in parallel against one context, which they don't (Surefire runs them one at a time).

## ADR-032: `VariantStatus.next` holds every transition rule; repeated actions are 409

**Context:** The approval flow has three actions, four statuses and one extra guard: a flagged variant (`validationPassed=false`) can't be submitted. If the rules were spread across the service, the controller and the entity, a new endpoint could skip one of them.
**Decision:** `VariantStatus.next(VariantAction action, boolean validationPassed)` is the only place that decides. It returns the target status or throws `IllegalTransitionException`, which carries the current status, the attempted action and a message for the API client. The transition table is one `switch`. The status is checked before the validation flag, so a flagged variant gets the flag message only for the one transition the flag guards (`DRAFT` → submit). The message parts that name statuses ("allowed only from PENDING_REVIEW", "APPROVED and REJECTED are final") are derived from the same table. `AdVariant.submit/approve/reject` only call `next` and store its result (and the reason); `reject` changes nothing if the transition is refused. Repeating an action, such as approving an `APPROVED` variant, is 409, not an idempotent 200: flow.md says anything outside the table is 409, and a client that retries after a lost response can read the variant to see where it landed.
**Consequences:** `VariantStatusTests` has one row for each of the 24 status × action × flag combinations, plus a loop over the enums that fails if anything outside the five allowed combinations starts being allowed. A flagged variant can only ever be `DRAFT`, so `PENDING_REVIEW` with `validationPassed=false` doesn't occur in practice; the table still defines it (approve and reject are allowed), so the rule is total.

## ADR-033: `@Version` optimistic locking on review actions; ETag/If-Match rejected

**Context:** Each review action reads a variant, asks `VariantStatus.next` whether the action is allowed, and writes the new status. Two requests can read the same row before either commits, for example an approve and a reject on the same `PENDING_REVIEW` variant. Both pass the state check against what they read. Without a version check, the second `UPDATE` overwrites the first, and the variant ends up `REJECTED` after the other client got a 200 saying it was `APPROVED`.
**Decision:** `AdVariant.version` is a JPA `@Version` (the column has been in V1 since M1). The service changes the loaded entity and calls `saveAndFlush`, so Hibernate issues `update … where id = ? and version = ?` inside the request's transaction. If another transaction has committed in between, no row matches. Spring raises `ObjectOptimisticLockingFailureException`, the transaction rolls back, and `GlobalExceptionHandler` returns 409 "The variant was modified concurrently. Reload it and retry.", logged at INFO. No JPQL bulk updates touch variants, because those would skip the check.
- **What it protects:** concurrent requests whose read-check-write windows overlap. The first commit wins; the other gets 409 and writes nothing.
- **What it doesn't protect, and doesn't need to:** stale client views. A client that read `PENDING_REVIEW` an hour ago never sends a version. Its request loads the current row and the state machine refuses the action with its own 409 (ADR-032).
- **Considered and rejected: ETag/If-Match.** The response would carry the version as an ETag, and actions would require `If-Match`, answering 412 on a mismatch and 428 when it's missing. That lets a client say "only if it's still the variant I saw". Here the copy never changes after generation and every transition is one-way, so the only thing a stale view can miss is a status change, and the state machine already catches that. If-Match would add header handling, two more status codes and work for every client, without blocking any outcome the state machine allows. Revisit if variants become editable: then a reviewer could approve copy they never saw, and If-Match would be the fix.

**Consequences:** `VariantOptimisticLockingTests` proves this over HTTP without racing threads. A `@MockitoSpyBean` on `AdVariantRepository` makes `findById` load the variant into the reject request's transaction (version 1), then approve it in a separate `REQUIRES_NEW` transaction that commits (version 2), before returning the stale entity. The reject gets 409, and the row stays `APPROVED` at version 2 with no rejection reason. A control test without the interference gets 200. With `@Version` removed (and the field initialized so the not-null column is still filled), the stale reject returns 200 and lands, so the test fails, as it should. The detail says "variant" because `AdVariant` is the only versioned entity; it needs generalizing if another entity gets a version.

## ADR-034: Multi-stage Docker image with a Maven cache mount and Boot's layered extraction

**Context:** M5 needs a container image for the compose stack now and AWS in M6. It must build from a clean clone, rebuild quickly after a code change, run as non-root, size its heap from the container, and never contain `.env` or a key.
**Decision:**
- **Two stages, pinned tags.** Build on `eclipse-temurin:21.0.13_9-jdk-noble`, run on `eclipse-temurin:21.0.13_9-jre-noble` (the JDK installed locally is the same 21.0.13+9).
- **No tests in the image build.** `./mvnw package -Dmaven.test.skip=true`. CI already runs every test with `verify`, and the integration tests need Testcontainers, which needs a Docker daemon that `docker build` can't reach. `maven.test.skip` (rather than `skipTests`) doesn't compile the tests either, so `src/test` stays out of the build context.
- **Dependencies through a BuildKit cache mount** (`--mount=type=cache,target=/root/.m2`). It also holds the wrapper's Maven download. A code-only change reruns the Maven step without downloading anything: measured with a one-line source change, 0 downloads and 8.5s for that step, against 748 artifacts on the cold build.
  - Rejected: a `dependency:go-offline` layer. It misses artifacts that plugins resolve only when they run, so a code change would still download some, and it adds a step.
  - Trade-off: GitHub runners don't keep the mount, so the CI image build downloads everything each run. That job only proves the Dockerfile builds.
- **Boot 4.1's layered extraction.** `java -Djarmode=tools -jar application.jar extract --layers --destination extracted`, then the four layers (`dependencies`, `spring-boot-loader`, `snapshot-dependencies`, `application`) are copied least-changing first, and the app runs with `java -jar application.jar`. Checked on our jar: `list-layers` prints those four, and the Boot 3 `-Djarmode=layertools` fails with "Unsupported jarmode 'layertools'". The application layer is 119 kB against 56 MB of dependencies.
- **Runtime.** A system user `app` (uid 999, no login shell) runs the app; the files stay root-owned, so the process can't change its own code. Port 8080 is exposed. `JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"`: container support is on by default in JDK 21, and the default 25% is too little for a single-process container. With `-m 512m`, `MaxHeapSize` is 384 MiB. It's an environment variable so it can be overridden at run time, at the cost of one "Picked up JAVA_TOOL_OPTIONS" line on stderr. A JVM that ran out of memory exits so the orchestrator restarts it, instead of limping on.
- **`.dockerignore` is an allowlist.** It ignores everything, then re-includes `mvnw`, `.mvn/`, `pom.xml` and `src/main/`, then excludes `**/.env` and `**/.env.*` again. `.env`, `.git`, `target/`, IDE folders, tests and docs never reach the daemon (build context: 122 kB). A new file, a secret included, can't slip in by default.
- Not done: a CDS or AOT-cache training run. It needs a context refresh, and that connects to the database.

**Consequences:** 509 MB unpacked, 150 MB compressed, most of it the JRE base. No `HEALTHCHECK` in the image: compose doesn't need one, and the M6 load balancer checks `/actuator/health` itself. Checked on the built image (commands in the M5 session log): no `.env` file, and neither the key from the local `.env`, `ANTHROPIC_API_KEY=` nor `sk-ant-` appears anywhere in the filesystem, the jars, the image config or the build history.

## ADR-035: The app runs in compose under an `app` profile; `DB_URL` is fixed to the service name

**Context:** `docker compose up -d` has started only Postgres since M0, for running the app on the host. M5 adds a full-stack option without changing that. The local `.env` serves both setups, and its `DB_URL` (when set) points at `localhost`, which inside a container is the container itself.
**Decision:**
- An `app` service with `profiles: [app]`, `build: .`, `image: franchiseads:local`, port 8080, and `depends_on: postgres: condition: service_healthy` (the existing `pg_isready` healthcheck).
- **`DB_URL`** is written literally as `jdbc:postgresql://postgres:5432/franchiseads`, not interpolated: `.env`'s `DB_URL` stays a host-run setting and can't break the container. `DB_USERNAME`/`DB_PASSWORD` use the same `${…:-franchiseads}` as the Postgres service, so the two always agree.
- **`ANTHROPIC_API_KEY: ${ANTHROPIC_API_KEY:-}`**: compose fills it at `up` time from the shell or `.env`, a shell value winning. The compose file has no value and the image has none (ADR-034). Without either it's empty, the app still starts, and generation answers 502 (ADR-021).
- **`ANTHROPIC_MODEL`/`ANTHROPIC_EFFORT`** repeat `application.yml`'s defaults. Passing an unset variable through as empty would override them: an empty model fails `@NotBlank` at startup.

**Consequences:**
- Plain `docker compose up -d` still starts only Postgres; `docker compose --profile app up -d --build` starts both.
- **Teardown needs the profile:** plain `docker compose down` stopped Postgres but left the app running (and the network, "still in use"). `docker compose --profile app down` removes both.
- The host port is 8080 for both ways of running, so a host `spring-boot:run` and the compose app can't run at the same time.
- Checked:
  - `docker compose config` shows the key from `.env` (length only), the shell override winning, and an empty key with an empty `--env-file`.
  - Inside the container the key had the same 106 characters, and none of the app's log lines contained it.
  - The app connected to `jdbc:postgresql://postgres:5432/franchiseads`, and `/actuator/health` from the host returned `UP`.

## ADR-036: springdoc-openapi 3.1.1; per-endpoint ProblemDetail responses; docs switchable

**Context:** M5 asks for an OpenAPI document and Swagger UI listing every endpoint with its success response and its `ProblemDetail` errors as in flow.md, switchable by a property because M6 decides whether they're public. springdoc isn't managed by Boot.
**Decision:**
- **Version 3.1.1** (`springdoc-openapi-starter-webmvc-ui`).
  - springdoc's compatibility table maps Boot 4.1.x to 3.1.x–3.2.x. 3.1.1's parent is Boot 4.1.0, and it resolves cleanly against our 4.1.2.
  - It has the 3.1.1 security fixes, including swagger-ui 5.32.14 for the DOMPurify XSS (CVE-2026-75838).
  - 3.2.0 (built on Boot 4.1.2, the version springdoc's page recommends) was released on 2026-10-24, a week before M5. The owner doesn't adopt dependency releases younger than about two weeks, and 3.2.0's changes (MCP, Scalar, nullable markers, Gradle Jackson pinning) don't matter here.
  - It brings Jackson 2 (`jackson-databind` 2.21.7, the Boot-managed version) in through swagger-core, next to the app's Jackson 3. Every existing test that asserts JSON responses (timestamps, problem details) passes unchanged with it on the classpath.
- **Switch:** `springdoc.api-docs.enabled` and `springdoc.swagger-ui.enabled` both read `API_DOCS_ENABLED` (default `true`). Off, `/v3/api-docs`, `/swagger-ui.html` and `/swagger-ui/index.html` answer the usual 404 `ProblemDetail`.
- **Error responses, declared per endpoint.** `@ProblemResponses({HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND, ...})` on each handler method. An `OperationCustomizer` (`OpenApiConfiguration`) turns each status into a `$ref` to one shared response per status (`Problem400` … `Problem504`). Each has a description worded after flow.md's error table and `application/problem+json` content with one `ProblemDetail` schema, which documents the `errors`, `currentStatus` and `attemptedAction` extensions. A status without a shared definition fails startup.
  - Each controller method gets `@Operation(summary)`, `@ProblemResponses`, and `@ApiResponse(responseCode = "201", useReturnTypeSchema = true)` on the four creates. One `@Tag` per controller.
  - Rejected: a global error list on every operation, which would contradict flow.md (`GET /brands` can't return 422). Also rejected: a path→errors table inside the configuration, which separates the docs from the endpoints they describe.
- **`springdoc.override-with-generic-response: false`.** With it on, springdoc copies every `@ExceptionHandler` that declares a `@ResponseStatus` onto every operation. Today none does: our handlers return `ProblemDetail`, so the setting changes nothing, and the M5 plan was wrong to say the default would put 502/503/504 on `GET /brands`. It's kept as a guard. With the setting on and `@ResponseStatus(NOT_FOUND)` added to the 404 handler, `GET /brands` gained a 404 and two tests failed; with it off, the same change passed.
- 500 isn't documented per endpoint; any endpoint can return it, with a generic detail.

**Consequences:** `OpenApiDocumentationTests` reads the endpoints from Spring MVC's own `RequestMappingHandlerMapping` (controllers in `com.franchiseads`) and checks that:
- the document has exactly those operations
- each has a summary and exactly one success response (201 for the creates)
- each lists exactly the error statuses in the test's table taken from flow.md, which a new endpoint must be added to
- every error response points at the shared definitions

Each check was shown to fail on a deliberate break: an endpoint hidden with `@Hidden`, a summary removed, 504 dropped from generate, the 201 declaration removed (springdoc then documents 200), and the generic-response case above. `ApiDocsDisabledTests` failed (200/302/200) with the switch hard-coded on. The test table and `@ProblemResponses` have to be kept in step with flow.md by hand.
