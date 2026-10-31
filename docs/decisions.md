# Decisions

## ADR-001: Spring Boot 4.1 over 3.5

- **Context:** New project. Open-source support for Spring Boot 3.5 has ended.
- **Decision:** Use Spring Boot 4.1.x (4.1.2 at creation), generated from Spring Initializr.
- **Consequences:** We stay on a supported line. Boot 4 renamed starters (e.g. `spring-boot-starter-webmvc`), split test support into per-technology `-test` starters, moved to Jackson 3, and ships JUnit Jupiter 6 and Testcontainers 2. Most examples online are Boot 3, so APIs must be checked against the resolved jars.

## ADR-002: Flyway owns the schema; Hibernate `ddl-auto: validate`

- **Context:** Hibernate auto-DDL (`update`/`create`) produces schema changes that are not reviewed, versioned or reproducible across environments.
- **Decision:** All schema changes are Flyway migrations in `src/main/resources/db/migration`. Hibernate runs with `ddl-auto: validate` and never changes the schema.
- **Consequences:** Every entity change needs a migration. Entity/schema drift fails at startup instead of silently changing the database.

## ADR-003: Testcontainers Postgres over H2 for tests

- **Context:** H2 differs from Postgres in SQL dialect, types and constraint behaviour, so tests could pass on H2 and fail in production.
- **Decision:** Tests run against a real `postgres:16` container via Testcontainers and `@ServiceConnection`.
- **Consequences:** Tests match production behaviour and run the real Flyway migrations. Running tests requires Docker (available locally and on GitHub's ubuntu runners), and the first run is slower while the image is pulled.
