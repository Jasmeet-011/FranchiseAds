# FranchiseAds

A Spring Boot service that writes localized Google Ads copy for every location of a franchise brand using the Claude API, and refuses to let invalid copy anywhere near approval.

> **Status:** in progress, built milestone by milestone. See [docs/progress.md](docs/progress.md).

## What it does

A franchise brand has dozens of locations, each with its own city and local offer. Writing ad copy for each by hand doesn't scale, and raw LLM output can't be trusted: it overshoots character limits, uses words the brand bans, and repeats itself.

FranchiseAds generates copy per location, then checks every variant with a deterministic validator: Google Ads character limits (30 per headline, 90 per description), banned brand words, duplicates, and editorial rules. A failing variant gets one regeneration with the violations fed back to the model. If it still fails, it's saved as flagged with its violations listed, and it can't be submitted for review. Valid variants move through DRAFT → PENDING_REVIEW → APPROVED or REJECTED.

## Tech stack

Java 21, Spring Boot 4.1, Spring Data JPA, PostgreSQL 16, Flyway, Claude API via `RestClient`, JUnit, Mockito, WireMock, Testcontainers, Docker, GitHub Actions.

## Running locally

Prerequisites: JDK 21 (with `JAVA_HOME` pointing at it) and Docker.

1. Start Postgres 16:

   ```bash
   docker compose up -d
   ```

   This creates database `franchiseads` on `localhost:5432`, stored in the named volume `postgres-data`.

2. Run the app:

   ```bash
   ./mvnw spring-boot:run
   ```

3. Check it is up:

   ```bash
   curl localhost:8080/actuator/health
   # {"groups":["liveness","readiness"],"status":"UP"}
   ```

4. Stop Postgres when done: `docker compose down` (add `-v` to also delete the data volume).

### Configuration

All configuration comes from environment variables (see [.env.example](.env.example)). With nothing set, the app and docker-compose both default to user `franchiseads` / password `franchiseads` on `jdbc:postgresql://localhost:5432/franchiseads`, so the steps above work with no setup.

To use other credentials, `cp .env.example .env` and edit it. Docker Compose reads `.env` automatically, but Spring Boot does not. Export the same `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` in your shell before `./mvnw spring-boot:run`. Postgres only applies the credentials when the volume is first created, so run `docker compose down -v` after changing them.

## Roadmap

- [x] M0: Skeleton, CI, docs
- [ ] M1: Domain model and CRUD
- [ ] M2: Claude API client with retries and timeouts
- [ ] M3: Validator and generation endpoint
- [ ] M4: Approval workflow and end-to-end test
- [ ] M5: Docker image, OpenAPI docs
- [ ] M6: AWS deployment

## Docs

- [Plan](docs/plan.md): milestones, scope, acceptance criteria
- [Flows](docs/flow.md): generate flow, approval state machine, retry policy, error mapping
- [Decisions](docs/decisions.md): why things are built the way they are
- [Progress](docs/progress.md): status, session log, agent mistakes log

## Built with Claude Code

This project is built with Claude Code as the primary coding agent, steered by [CLAUDE.md](CLAUDE.md). Every mistake the agent made and how it was caught is logged in [docs/progress.md](docs/progress.md).
