# FranchiseAds

A Spring Boot REST service that generates localized Google Ads copy for each location of a franchise brand using the Claude API.

**Status:** in progress, built milestone by milestone, see [docs/progress.md](docs/progress.md).

## Overview

TODO

## Architecture

TODO

## Running Locally

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

## API

TODO

## Testing

TODO

## Design Decisions

TODO

## Working with Claude Code

TODO

## Deployment

TODO
