# CLAUDE.md

Guide for Claude Code working in this repo. Read it fully at the start of every session.

## Project

FranchiseAds is a Spring Boot REST service that generates localized Google Ads copy (responsive search ads) for each location of a franchise brand using the Claude API.

The model's output is never trusted. Every generated variant goes through a deterministic validator. A failing variant gets exactly one regeneration attempt with the violations fed back to the model. If it still fails, it is saved as flagged and cannot enter review. Valid variants move through an approval workflow: DRAFT → PENDING_REVIEW → APPROVED or REJECTED.

This is a portfolio project. Correctness, test quality, and honest documentation matter more than feature count.

## Start of every session

1. Read `docs/progress.md`: current milestone, what's done, what's next.
2. Read that milestone in `docs/plan.md`: scope, acceptance criteria, out of scope.
3. Check `docs/decisions.md` before any design choice. Don't contradict an accepted decision without asking me.
4. `docs/flow.md` is the spec for the generate flow, the approval flow, and error mapping. Code must match it. If behavior should change, update flow.md in the same commit.

## Stack

- Java 21, Maven wrapper
- Spring Boot 4.1.x: Web MVC, Data JPA (Hibernate), Validation, Actuator, Flyway
- PostgreSQL 16 (docker-compose locally, Testcontainers in tests)
- Tests: JUnit (version managed by Boot, currently 6), Mockito, MockMvc, Testcontainers 2.x, WireMock (`org.wiremock:wiremock-standalone` 3.13.2, shaded; ADR-019)
- API docs: springdoc-openapi 3.1.1 (`springdoc-openapi-starter-webmvc-ui`, ADR-036), switched by `API_DOCS_ENABLED`
- Packaging: multi-stage `Dockerfile` (Temurin 21.0.13+9 JDK/JRE, ADR-034), `docker-compose.yml` with the app under the `app` profile (ADR-035)

## Commands

Prerequisites: `JAVA_HOME` points at a JDK 21 (the wrapper uses it; a shell started before it was set won't see it), Docker running.

Windows (PowerShell):

```
.\mvnw.cmd verify                 # build + all tests (Docker must be running)
.\mvnw.cmd test -Dtest=ClassName  # one test class
.\mvnw.cmd test "-Dtest=com.franchiseads.ClassName#method"  # quote -D args containing '.' in PowerShell
docker compose up -d              # local Postgres
docker compose down               # stop it (volume kept)
docker build -t franchiseads:local .                # the image (tests skipped inside; ADR-034)
docker compose --profile app up -d --build          # app + Postgres in Docker, app on :8080
docker compose --profile app down                   # plain "down" leaves the app container running
.\mvnw.cmd spring-boot:run        # run against docker-compose Postgres
.\mvnw.cmd spring-boot:test-run   # run with a Testcontainers Postgres, no compose needed
curl http://localhost:8080/actuator/health
.\mvnw.cmd test -Plive            # only the live Claude smoke test; real API call, needs ANTHROPIC_API_KEY. Owner runs it, never CI
```

If `JAVA_HOME` is empty in your shell, set it for the command from the user environment: `$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','User')`.

Linux / CI: same commands with `./mvnw` (CI runs `./mvnw -B verify`).

## Spring Boot 4 rules

Most of your training data is Boot 3. Boot 4 differs, and you will be wrong if you go from memory:

- Starters: `spring-boot-starter-webmvc` (not `-web`), `spring-boot-starter-flyway`, and per-module `-test` starters.
- Jackson 3: core classes live under `tools.jackson.*` (e.g. `tools.jackson.databind.ObjectMapper`). Annotations did not move: `@JsonProperty` etc. are still `com.fasterxml.jackson.annotation.*`. Check every Jackson import.
- `@MockBean` is gone. Use `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`).
- `@WebMvcTest` and `@AutoConfigureMockMvc` are in `org.springframework.boot.webmvc.test.autoconfigure`.
- `TestRestTemplate` moved to `org.springframework.boot.resttestclient` and isn't auto-configured. Use MockMvc for slice tests, `RestTestClient` (`org.springframework.test.web.servlet.client`, enabled with `@AutoConfigureRestTestClient`) for full-server tests.
- `RestTestClient` status assertions have `isEqualTo(int)` but no `isConflict()`; check the jar before using a named status method.
- `@DataJpaTest` is `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`. With `@Import(TestcontainersConfiguration.class)` it uses the Testcontainers Postgres (no embedded replacement) and runs Flyway + `validate`.
- Testcontainers 2.x: `testcontainers-postgresql`, `org.testcontainers.postgresql.PostgreSQLContainer`, no type parameter. The old generic `org.testcontainers.containers.PostgreSQLContainer` still exists but is deprecated; don't use it.
- Hibernate 7: `validate` does not check `text[]` column types (ADR-016). Every array column needs a round-trip test.

Rule: confirm every Spring class, annotation, import, and property exists in the resolved dependencies before using it. If something you expected doesn't exist, say so, and log it if you got it wrong first.

## Project structure

Package by feature. Each feature owns its entity, repository, service, controller, and DTOs.

Exists today (end of M5):

```
src/main/java/com/franchiseads/
  brand/      Brand, BrandRepository, BrandService, BrandController, dto/ (CreateBrandRequest, BrandResponse)
  location/   Location, LocationRepository, LocationService, LocationController, dto/
  campaign/   Campaign, CampaignRepository, CampaignService, CampaignController, dto/
  variant/    AdVariant (@Version; submit/approve/reject), AdVariantRepository, VariantStatus (next(): the only
              transition rules), VariantAction, IllegalTransitionException, VariantService (saveGenerated, list, get,
              submit, approve, reject), VariantController (GET + action endpoints), GeneratedVariant,
              dto/ (VariantResponse, RejectRequest)
  claude/     ClaudeClient (retry loop, optional deadline), ClaudeProperties (incl. effort), ClaudeConfiguration,
              ClaudeResponse, Sleeper, Claude*Exception
  generation/ GenerationService (orchestration, not @Transactional), GenerationInputLoader (read-only tx),
              GenerationController, GenerationProperties (deadline), GenerationConfiguration (beans for the pure
              classes), GenerationInputs, LocationBrandMismatchException, dto/GenerateRequest,
              AdBrief, Prompt, PromptBuilder, AdCopy, AdCopyParser, MalformedAdCopyException (pure)
  validation/ AdCopyValidator, Violation (pure), AdCopyRules (+ AdCopyRulesConfiguration)
  common/     Strings (input normalization), config/ClockConfiguration, error/ (GlobalExceptionHandler,
              ResourceNotFoundException), openapi/ (OpenApiConfiguration, @ProblemResponses: per-endpoint
              ProblemDetail responses as shared $refs)
src/main/resources/application.yml         all config, env vars with local defaults
src/main/resources/db/migration/V1__init.sql
src/test/java/com/franchiseads/
  TestcontainersConfiguration.java         public; postgres:16 bean with @ServiceConnection; @Import it in DB tests
  TestFranchiseadsApplication.java         entry point for spring-boot:test-run
  TruncateTablesExtension.java             @ExtendWith it on every test that writes through a full context (+ Tests)
  ApprovalWorkflowEndToEndTests.java       real ClaudeClient against WireMock + Postgres, all over HTTP
  PersistenceMappingTests.java             @DataJpaTest: V1 + validate, text[] round trips, unique index
  <feature>/  *ControllerTests (@WebMvcTest), *ServiceTests (Mockito), *ApiIntegrationTests (HTTP + Postgres)
  common/error/GlobalExceptionHandlerTests (stub controller in test sources)
  claude/     ClaudeClientTests (WireMock, RecordingSleeper, MutableClock), ClaudePropertiesTests, ClaudeLiveSmokeTests (@Tag("live"))
  generation/ GenerationServiceTests (Mockito + real pure helpers), GenerationTransactionBoundaryTests (no tx during
              Claude calls), GenerationApiIntegrationTests (HTTP + Postgres, ClaudeClient mocked)
  validation/ AdCopyValidatorTests (plain JUnit)
  variant/    VariantStatusTests, AdVariantTests (plain JUnit), VariantOptimisticLockingTests (@Version over HTTP,
              deterministic via @MockitoSpyBean on the repository)
  common/openapi/ OpenApiDocumentationTests (every mapped endpoint documented, errors per flow.md), ApiDocsDisabledTests
Dockerfile, .dockerignore (allowlist), docker-compose.yml, .env.example, .github/workflows/ci.yml (verify + image build), docs/
```

Tests that write through a full Spring context declare `@ExtendWith(TruncateTablesExtension.class)`: every application table (not `flyway_schema_history`) is truncated with `RESTART IDENTITY CASCADE` before each test (ADR-031), so no test sees another's rows and IDs start at 1. `@DataJpaTest` classes roll back instead and don't need it.

Target layout (packages are created as milestones land):

```
com.franchiseads
├── brand/        Brand, BrandRepository, BrandService, BrandController, dto/
├── location/     Location, ...
├── campaign/     Campaign, ...
├── variant/      AdVariant, VariantStatus, VariantService, VariantController, dto/
├── generation/   GenerationService (orchestration), PromptBuilder, AdCopyParser
├── claude/       ClaudeClient, ClaudeProperties, Claude*Exception
├── validation/   AdCopyValidator, Violation, AdCopyRules
└── common/       error/ (ProblemDetail handler, exceptions), config/
```

## Conventions

- Controllers are thin: validate input, call one service method, map to a DTO. No business logic, no repository access.
- DTOs are Java records in each feature's `dto` package. Never return a JPA entity from a controller.
- Manual mapping with static `from(...)` methods. No MapStruct, no Lombok.
- Constructor injection only. No field `@Autowired`.
- IDs are `Long` with IDENTITY. Timestamps are `Instant` mapped to `timestamptz`.
- String lists (banned words, headlines, descriptions, violations) are Postgres `text[]` columns mapped as `List<String>`.
- Errors: RFC 9457 `ProblemDetail` from one `@RestControllerAdvice` in `common/error`. Status codes are listed in `docs/flow.md`. Throw a specific exception per case. Never catch and swallow.
- `@Transactional` goes on service methods, never controllers. Never hold a database transaction open across a Claude API call.
- Anything that differs between environments comes from env vars through `@ConfigurationProperties` records. No secrets in `application.yml`, code, tests, or logs.
- Only Flyway changes the schema. Never edit a committed migration; add a new `V<n>__description.sql`. `ddl-auto` stays `validate`.
- Logging: SLF4J. Log each Claude call's attempt number, HTTP status, and latency. Never log the API key. Full prompts only at DEBUG.

## Testing rules

- `verify` must pass before every commit. CI runs it on Linux with no API key and no `.env`.
- A local `.env` is also loaded during tests (`spring.config.import` in `application.yml`). Tests must not depend on it: set every property a test relies on in the test itself.
- Tests never call the real Claude API. WireMock only.
- Pure logic (validator, state transitions, prompt builder, parser) gets plain JUnit tests with no Spring context.
- Services: Mockito. Controllers: `@WebMvcTest` + MockMvc. Persistence: Testcontainers Postgres.
- Retry backoff must be injectable so tests don't actually sleep.
- Test names state behavior: `rejectsHeadlineLongerThan30Characters`, not `testValidator2`.
- Fixing a bug: write the failing test first, then fix.
- Never delete a test, `@Disabled` it, or loosen an assertion to get green. If a test looks wrong, stop and tell me.

## Milestone workflow

1. Work only on the current milestone from `docs/progress.md`. Don't start the next one until I say so.
2. Propose a plan first and wait for approval.
3. Small commits with conventional messages: `feat:`, `fix:`, `test:`, `docs:`, `chore:`, `refactor:`. Don't push; I push.
4. Before ending a session:
   - `verify` passes
   - `docs/progress.md` updated: checklist, session log entry, Agent Mistakes Log
   - an ADR added to `docs/decisions.md` for every non-obvious choice
   - `docs/flow.md` updated if behavior changed
5. End with a report: what's done, test results, deviations from plan.md, open questions.

## Agent Mistakes Log

Whenever something you did turns out wrong (caught by the compiler, a test, me, or your own re-check), add a row to the log in `docs/progress.md`: what you did, how it was caught, and the fix. Be specific and honest. Don't invent entries, don't skip embarrassing ones. This log is a deliverable, not a formality.

## Don'ts

- No new dependencies without asking me first.
- No Lombok, MapStruct, H2, or `ddl-auto` other than `validate`.
- No auth, Kafka or queues, real Google Ads publishing, or a second LLM provider. Out of scope.
- Never trust model output: everything from Claude goes through the parser and the validator.
- Never retry a 4xx other than 429.
- Never commit `.env` or any real key.
