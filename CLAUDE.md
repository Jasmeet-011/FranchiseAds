# CLAUDE.md

## Project

TODO

## Stack

- Java 21 (Temurin), Maven via the Maven wrapper (`./mvnw`, Maven 3.10.0)
- Spring Boot 4.1.2 (Spring Framework 7.0.x), generated from Spring Initializr
- Starters: `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa` (Hibernate 7), `spring-boot-starter-flyway` + `flyway-database-postgresql` (Flyway 12), `spring-boot-starter-validation`, `spring-boot-starter-actuator`
- PostgreSQL JDBC driver; Postgres 16 via `docker-compose.yml` for local dev
- Jackson 3 (`tools.jackson.*` packages), pulled in by Boot 4
- Tests: JUnit Jupiter 6 (via `spring-boot-starter-test`), Testcontainers 2.x (`testcontainers-postgresql`, `testcontainers-junit-jupiter`), `@ServiceConnection`
- CI: GitHub Actions (`.github/workflows/ci.yml`)

Boot 4 note: starters and test support are split per technology (each starter has a matching `-test` starter, e.g. `spring-boot-starter-webmvc-test`). Check class and package names against the resolved jars before using them.

## Commands

```bash
./mvnw -B verify              # build + run all tests (needs Docker running for Testcontainers)
./mvnw test                   # tests only
docker compose up -d          # start local Postgres 16 (db: franchiseads, port 5432)
./mvnw spring-boot:run        # run app against local Postgres on http://localhost:8080
curl localhost:8080/actuator/health
docker compose down           # stop Postgres (keeps the postgres-data volume)
docker compose down -v        # stop Postgres and delete its data volume
```

## Project Structure

```
.github/workflows/ci.yml        CI: Java 21 + ./mvnw -B verify on push/PR to main
docker-compose.yml              Local Postgres 16 with healthcheck and named volume
.env.example                    Env var template (DB_URL, DB_USERNAME, DB_PASSWORD, ANTHROPIC_API_KEY)
pom.xml                         Initializr-generated, Boot 4.1.2 parent
src/main/java/com/franchiseads/
  FranchiseadsApplication.java  Boot entry point
src/main/resources/
  application.yml               All config; values come from env vars with local defaults
  db/migration/                 Flyway migrations (empty, .gitkeep only)
src/test/java/com/franchiseads/
  TestcontainersConfiguration.java  postgres:16 container bean with @ServiceConnection
  FranchiseadsApplicationTests.java Context-loads test
  ActuatorHealthTests.java          GET /actuator/health -> 200, status UP
  TestFranchiseadsApplication.java  Initializr dev entry point: runs the app with the Testcontainers Postgres
docs/                           plan, flow, progress, decisions
```

## Conventions

TODO

## Testing Rules

TODO

## Milestone Workflow

TODO

## Don'ts

TODO
